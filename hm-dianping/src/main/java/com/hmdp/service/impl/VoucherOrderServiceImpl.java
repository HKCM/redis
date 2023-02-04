package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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

    @Override
    public Result seckillVoucher(Long voucherId) {

        // 查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 查询秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }

        // 秒杀哦是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }

        // 库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        // 一人一单判断
        // 获取用户ID
        Long userId = UserHolder.getUser().getId();

        // 单体模式下解决多线程并发问题，集群模式无效
        // intern 返回字符串常量对象
        synchronized (userId.toString().intern()) {
            // 获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucher(voucherId);
        }
    }

    @Transactional
    public Result createVoucher(Long voucherId) {
        // 获取用户ID
        Long userId = UserHolder.getUser().getId();

        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 在此之前数据并不存在所以只能使用悲观锁
        if (count > 0) {
            return Result.fail("您已下过订单");
        }

        // 扣减库存 乐观锁改进方案只要库存大于0就可以更新
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0).update();

        // 扣件库存失败
        if (!success) {
            return Result.fail("库存不足");
        }

        // 获取全局ID
        Long orderId = redisIdWorker.nextId("order");

        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        save(voucherOrder);
        return Result.ok(orderId);

    }
}
