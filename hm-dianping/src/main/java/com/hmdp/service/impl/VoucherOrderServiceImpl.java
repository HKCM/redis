package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private IVoucherOrderService proxy;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 借助Java实现消息队列
    private BlockingQueue<VoucherOrder> orderTask = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true){
                try {
                    // 获取Redis Stream队列中的消息
                    // XREADGROUP GROUP g1 consumer COUNT 1 BLOCK 2000 STREAMS stream.order >

                    List<MapRecord<String, Object, Object>> read = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );

                    // 判断消息是否获取成功
                    if (read == null || read.isEmpty()){
                        continue;
                    }

                    // 如果有返回，则解析数据
                    MapRecord<String, Object, Object> entries = read.get(0);
                    Map<Object, Object> map = entries.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(map, new VoucherOrder(), true);

                    // 创建订单
                    handleVoucherOrder(voucherOrder);
                    // ACK消息消费确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",entries.getId());

                } catch (Exception e) {
                    String redisPendingCommand = "XREADGROUP GROUP g1 consumer COUNT 1 BLOCK 2000 STREAMS s1 0";
                    log.error("处理订单异常",e);
                    handlePendingList();
                }

            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 获取Stream队列中的PendingList消息
                    // XREADGROUP GROUP g1 consumer COUNT 1 BLOCK 2000 STREAMS stream.order 0
                    List<MapRecord<String, Object, Object>> read = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );

                    // 判断PendingList消息队列中是否有未消费数据
                    if (read == null || read.isEmpty()) {
                        // 如果PendingList消息队列中没有消息 直接退出
                        break;
                    }

                    // 如果有返回，则解析数据
                    MapRecord<String, Object, Object> entries = read.get(0);
                    Map<Object, Object> map = entries.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(map, new VoucherOrder(), true);

                    // 创建订单
                    handleVoucherOrder(voucherOrder);
                    // ACK消息消费确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", entries.getId());

                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

// 旧逻辑：从本地的消息队列读取
//    private class VoucherOrderHandler implements Runnable{
//        @Override
//        public void run() {
//            while (true){
//                try {
//                    // 获取队列中的消息
//                    VoucherOrder voucherOrder = orderTask.take();
//                    // 创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (InterruptedException e) {
//                    log.error("处理订单异常",e);
//                }
//
//            }
//        }
//    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 获取用户ID
        Long userId = voucherOrder.getUserId();

        // 通过RedissonClient创建锁
        RLock lock = redissonClient.getLock("lock:order" + userId);
        boolean isLock = lock.tryLock();

        if (!isLock){
            // 获取锁失败直接返回
            log.error("不允许重复下单");
            return ;
        }
        try {
            proxy.createVoucher(voucherOrder);
        } finally {
            // 释放锁
            lock.unlock();
        }
    }



    /**
     * 新的秒杀逻辑通过lua脚本实现，但是没有判断秒杀的开始和结束时间
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取订单id
        Long orderId = redisIdWorker.nextId("order");

        Long userId = UserHolder.getUser().getId();
        // 调用lua脚本判断是否具有下单资格
        int result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(),orderId.toString()
        ).intValue();


        if (result != 0){
            // 返回值为1 表示库存不足
            // 返回值为2 表示重复下单
            return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
        }

        // 旧逻辑：如果可以下单则创建订单对象并加入本地阻塞队列
        // 新逻辑中：直接将订单信息加入了Stream队列
        // 如果返回值为0 表示可以下单
        // 创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setVoucherId(voucherId);
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//
//        // 加入异步队列
//        orderTask.add(voucherOrder);

        // 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();


        return Result.ok(orderId);
    }

//     旧逻辑：从数据库取数据判断是否能够下单
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//
//        // 查询优惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 查询秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//
//        // 秒杀哦是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//
//        // 库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//
//        // 一人一单判断
//        // 获取用户ID
//        Long userId = UserHolder.getUser().getId();
//
//        // 单体模式下解决多线程并发问题，集群模式无效
//        // intern 返回字符串常量对象
////        synchronized (userId.toString().intern()) {
////            // 获取代理对象
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucher(voucherId);
////        }
//
//
////        // 创建锁对象 这是自己实现的锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
////
////        // 获取全局唯一锁
////        boolean isLock = lock.tryLock(10);
//
//        // 通过RedissonClient创建锁
//        RLock lock = redissonClient.getLock("lock:order" + userId);
//        boolean isLock = lock.tryLock();
//
//        if (!isLock){
//            // 获取锁失败直接返回
//            return Result.fail("不允许重复下单");
//        }
//
//        try {
//            // 获取代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucher(voucherId);
//        } finally {
//            // 释放锁
//            lock.unlock();
//        }
//    }

    @Transactional
    public void createVoucher(VoucherOrder voucherOrder) {
        // 获取用户ID
        Long userId = voucherOrder.getUserId();

        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // 在此之前数据并不存在所以只能使用悲观锁
        if (count > 0) {
            log.error("您已下过订单");
            return ;
        }

        // 扣减库存 乐观锁改进方案只要库存大于0就可以更新
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0).update();

        // 扣件库存失败
        if (!success) {
            log.error("库存不足");
            return;
        }

        save(voucherOrder);
    }
}
