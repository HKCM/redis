package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

    // 开始的时间戳
    private static final Long BEGIN_TIMESTAMP = 1640995200L;

    private static final int COUNT_BITS = 32;



    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public Long nextId(String keyPrefix) {
        // 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 生成key
        // 获取当前日期
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String key = "icr:" + keyPrefix + ":" + date;
        // 生成序列号
        Long count = stringRedisTemplate.opsForValue().increment(key);

        // 将timestamp向左移 然后与count进行或运算
        return timestamp << COUNT_BITS | count;

    }
}
