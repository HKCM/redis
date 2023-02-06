package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 判断phone是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号码不合法");
        }

        //
        String code = RandomUtil.randomNumbers(6);

        // 模拟发送验证码
        log.debug("验证码发送成功，验证码：{}", code);

        // 在session中保存验证码
        // session.setAttribute("code",code);

        // 在redis中保存验证码
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 判断phone是否合法
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号码不合法");
        }

        String code = loginForm.getCode();

        // 从session中获取验证码
        // Object cacheCode = session.getAttribute("code");

        // 从redis中获取验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        // 检测用户是否存在
        User user = query().eq("phone", phone).one();

        if (user == null) {
            // 如果用户不存在，则创建新用户
            user = createUserWithPhone(phone);
        }

        // 在session中保存用户
        // session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        // 在redis中保存用户
        // 使用uuid作为用户的key
        String token = UUID.randomUUID().toString(true);

        // 将userDTO转为map对象方便一次性存入redis
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreCase(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        String tokenKey = LOGIN_USER_KEY + token;

        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    @Override
    public Result userInfo(Long userId) {
        // 查询详情
        User user = getById(userId);

        if (user == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        return Result.ok(userDTO);
    }

    /**
     * 用户签到功能
     *
     * @return
     */
    @Override
    public Result sign() {
        // 获取用户key
        Long userId = UserHolder.getUser().getId();

        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyy:MM"));

        String key = USER_SIGN_KEY + userId + keySuffix;
        // 获取当天
        int dayOfMonth = now.getDayOfMonth();

        // 按位进行签到保存
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        // TODO 可以报保存信息写入到数据库持久化

        return Result.ok();
    }

    /**
     * 签到统计功能
     * @return
     */
    @Override
    public Result signCount() {

        // 获取用户key
        Long userId = UserHolder.getUser().getId();

        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyy:MM"));

        String key = USER_SIGN_KEY + userId + keySuffix;
        // 获取当天
        int dayOfMonth = now.getDayOfMonth();

        // 获取签到的二进制bit的十进制返回
        List<Long> result = stringRedisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));

        // 如果用户从来没签到则为空
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }

        // 用户这个月没签到 num为0
        Long num = result.get(0);
        if (num == null || num ==0){
            return Result.ok(0);
        }
        // 从今天开始统计连续签到天数
        int count = 0;
        while (true){

            // 与1做与运算 得到最后一个bit位 判断是否为0
            if ((num & 1) == 0) {
                break;
            } else {
                count++;
            }

            // 把数字右移1位
            num >>>= 1;
        }

        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(8));
        user.setPhone(phone);
        return user;
    }
}
