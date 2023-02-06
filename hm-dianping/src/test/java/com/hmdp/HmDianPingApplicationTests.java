package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        // 多线程实现ID生成测试
        CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                redisIdWorker.nextId("order");
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("Time: " + (end - begin));
    }

    @Test
    void testLoadShopData() {
        // 查询店铺信息
        List<Shop> shopList = shopService.list();
        // 按照店铺ID分组
        Map<Long, List<Shop>> map = shopList.stream().collect(Collectors.groupingBy(shop -> shop.getTypeId()));
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long entryKey = entry.getKey();
            String key = SHOP_GEO_KEY + entryKey;
            List<Shop> value = entry.getValue();

            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());

            for (Shop shop : value) {
                // 一条一条写入redis，每次都需要连接redis
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(),shop.getY()),shop.getId().toString());

                // 先将坐标加入Location集合 一次性提交
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

}
