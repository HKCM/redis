package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {

    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 设置逻辑过期时间
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisData.setData(value);

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {

        String key = keyPrefix + id;

        // 查询redis中是否存在，存在则直接返回
        String json = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }

        // 如果缓存命中但是为空，此时返回值为null 说明这是一个故意缓存的无效key 返回
        if (json != null) {
            return null;
        }

        // 如果返回值为null 说明在redis中不存在, 去数据库中查询
        // 函数式编程
        R r = dbFallback.apply(id);

        if (r == null) {
            // 如果数据库中没有 将空值写入缓存 缓解缓存穿透问题
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 如果数据库中有则写入redis缓存，设置时间
        this.set(key,r,time,unit);

        return r;
    }

    // 独立的线程池进行缓存重建
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 通过逻辑过期解决缓存击穿问题
     *
     * @param id
     * @return
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        // 查询redis中是否存在，不存在则直接返回
        // 因为使用逻辑过期会提前预热，将所有热点数据都加入缓存
        // 如果缓存未命中，那数据库中也没有
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(redisDataJson)) {
            return null;
        }

        // 命中后将Json反序列化为对象
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 如果未过期则直接返回
            return r;
        }

        // 如果过期，则缓存重建
        // 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        // 获取锁成功，开启独立线程实现缓存重建
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            // 再次检查缓存是够过期
            redisDataJson = stringRedisTemplate.opsForValue().get(key);
            // 命中后将Json反序列化为对象
            redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
            // 获取最新的过期时间
            expireTime = redisData.getExpireTime();
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 如果未过期则直接返回
                return r;
            }

            // 开启独立线程进行缓存重建
            try {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    // 查询数据库
                    R r1 = dbFallback.apply(id);
                    // 逻辑过期时间
                    this.setWithLogicalExpire(key,r1,time,unit);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                unLock(lockKey);
            }
        }

        return r;
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

}
