package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokeInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokeInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 获取token
        String token = request.getHeader("authorization");
        // 如果token为空 直接拦截
        if (StrUtil.isBlank(token)) {
            return true;
        }

        // 从redis中获取用户
        String hashKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(hashKey);

        // userMap不存在则拦截(说明redis用户缓存过期)
        if (userMap.isEmpty()){
            return true;
        }

        // 将map对象转为userDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);

        // 用户存在，将用户信息放入ThreadLocal
        UserHolder.saveUser(userDTO);

        // 刷新token有效期
        stringRedisTemplate.expire(hashKey,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }


}
