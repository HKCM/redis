package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 缓解缓存穿透
        // Shop shop = queryWithPassThrough(id);

        // 通过互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        // 返回数据
        return Result.ok(shop);
    }

    private boolean tryLock(String key) {
        // 通过setIfAbsent 如果能设置代表是第一个拿到锁的线程
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 3, TimeUnit.SECONDS);
        // Boolean 如果值为空会有空指针异常，使用BooleanUtil避免这个问题
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 缓存穿透问题缓解方案，将空值写入
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;

        // 查询redis中是否存在，存在则直接返回
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        if (shopJson == ""){
            // 如果缓存命中但是为空，说明这是一个无效的key
            return null;
        }

        // 如果在redis中不存在，去数据库中查询
        Shop shop = getById(id);

        if (shop == null) {
            // 如果数据库中没有 将空值写入缓存 缓解缓存穿透问题
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 如果数据库中有则写入redis缓存，设置时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }

    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;

        // 查询redis中是否存在，存在则直接返回
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        if (shopJson == ""){
            // 如果缓存命中但是为空，说明这是一个无效的key
            return null;
        }

        // 缓存重建
        // 尝试获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = !tryLock(lockKey);
            // 获取锁失败则休眠并重试
            if (!isLock) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 再次检查缓存是否存在，因为可能缓存刚重建完成，被其他线程获取到锁的情况 避免反复重建
            shopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson)) {
                return JSONUtil.toBean(shopJson, Shop.class);
            }

            // 成功获取锁，去数据库中查询
            shop = getById(id);

            // 模拟重建锁的延迟
            // Thread.sleep(250);

            if (shop == null) {
                // 如果数据库中没有，将空值写入缓存，缓解缓存穿透问题
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 如果数据库中有则写入redis缓存，设置时间
            // 添加随机数缓解缓存雪崩
            int randomInt = RandomUtil.randomInt(0, 10);
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL + randomInt, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unLock(lockKey);
        }

        return shop;
    }


    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();

        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 将数据写入数据库
        updateById(shop);

        // 删除缓存
        String key = CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key);

        return null;
    }
}
