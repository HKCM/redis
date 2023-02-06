package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
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

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {

        // 通过工具类缓解缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY, id, Shop.class, (id2) -> getById(id2), CACHE_SHOP_TTL, TimeUnit.MINUTES
        );

        // 通过互斥锁解决缓存击穿
        // Shop shop = queryWithMutex(id);

        // 通过工具类逻辑过期解决缓存击穿
        // 注意： 逻辑过期需要先预热数据库
//        Shop shop = cacheClient.queryWithLogicalExpire(
//                CACHE_SHOP_KEY, id, Shop.class, (id2) -> getById(id2), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        // 返回数据
        return Result.ok(shop);
    }

//    private boolean tryLock(String key) {
//        // 通过setIfAbsent 如果能设置代表是第一个拿到锁的线程
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 3, TimeUnit.SECONDS);
//        // Boolean 如果值为空会有空指针异常，使用BooleanUtil避免这个问题
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unLock(String key) {
//        stringRedisTemplate.delete(key);
//    }

    /**
     * 缓存穿透问题缓解方案，将空值写入
     *
     * @param id
     * @return
     */
//    public Shop queryWithPassThrough(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//
//        // 查询redis中是否存在，存在则直接返回
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        if (StrUtil.isNotBlank(shopJson)) {
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//
//        if (shopJson == "") {
//            // 如果缓存命中但是为空，说明这是一个无效的key
//            return null;
//        }
//
//        // 如果在redis中不存在，去数据库中查询
//        Shop shop = getById(id);
//
//        if (shop == null) {
//            // 如果数据库中没有 将空值写入缓存 缓解缓存穿透问题
//            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//
//        // 如果数据库中有则写入redis缓存，设置时间
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        return shop;
//    }

    /**
     * 互斥锁解决缓存击穿问题
     *
     * @param id
     * @return
     */
//    public Shop queryWithMutex(Long id) {
//        String key = CACHE_SHOP_KEY + id;
//
//        // 查询redis中是否存在，存在则直接返回
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        if (StrUtil.isNotBlank(shopJson)) {
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//
//        if (shopJson == "") {
//            // 如果缓存命中但是为空，说明这是一个无效的key
//            return null;
//        }
//
//        // 缓存重建
//        // 尝试获取锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean isLock = !tryLock(lockKey);
//            // 获取锁失败则休眠并重试
//            if (!isLock) {
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//
//            // 再次检查缓存是否存在，因为可能缓存刚重建完成，被其他线程获取到锁的情况 避免反复重建
//            shopJson = stringRedisTemplate.opsForValue().get(key);
//            if (StrUtil.isNotBlank(shopJson)) {
//                return JSONUtil.toBean(shopJson, Shop.class);
//            }
//
//            // 成功获取锁，去数据库中查询
//            shop = getById(id);
//
//            // 模拟重建锁的延迟
//            // Thread.sleep(250);
//
//            if (shop == null) {
//                // 如果数据库中没有，将空值写入缓存，缓解缓存穿透问题
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//
//            // 如果数据库中有则写入redis缓存，设置时间
//            // 添加随机数缓解缓存雪崩
//            int randomInt = RandomUtil.randomInt(0, 10);
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL + randomInt, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            // 释放锁
//            unLock(lockKey);
//        }
//
//        return shop;
//    }


//    public void saveShop2Redis(Long id, Long expireSeconds) {
//        // 查询店铺数据
//        Shop shop = getById(id);
//        // 封装逻辑过期
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        // 写入redis,这里放入时时永久有效的 只有逻辑过期时间
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//
//    }
    @Override
    @Transactional
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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 是否需要根据坐标查询
        if (x == null || y== null){
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        String key = SHOP_GEO_KEY + typeId;
        // 分页的起始页码
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 逻辑分页，这里是获取所有的前N条
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = stringRedisTemplate.opsForGeo()
                .search(
                    key,
                    GeoReference.fromCoordinate(x,y),
                    new Distance(5000),
                    RedisGeoCommands.GeoRadiusCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );


        // 如果没有结果则直接返回空
        if (geoResults == null) {
            return Result.ok(Collections.emptyList());
        }

        // 解析商户ID
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = geoResults.getContent();


        if (list.size() <= from){
            // 表示已查到最后了
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());

        list.stream().skip(from).forEach(results -> {
            String shopId = results.getContent().getName();
            ids.add(Long.valueOf(shopId));
            distanceMap.put(shopId,results.getDistance());
        });
        // 根据类型分页查询
        String idsStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD (id," + idsStr + ")").list();
        //加入距离
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        // 返回数据

        return Result.ok(shops);
    }
}
