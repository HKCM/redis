package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 判断phone是否合法
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号码不合法");
        }

        //
        String code = RandomUtil.randomNumbers(6);

        // 模拟发送验证码
        log.debug("验证码发送成功，验证码：{}",code);

        // 在session中保存验证码
        session.setAttribute("code",code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 判断phone是否合法
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号码不合法");
        }

        // 获取验证码
        String code = loginForm.getCode();
        Object cacheCode = session.getAttribute("code");

        if (cacheCode == null || !cacheCode.toString().equals(code)){
            return Result.fail("验证码错误");
        }

        // 检测用户是否存在
        User user = query().eq("phone",phone).one();

        if (user == null) {
            // 如果用户不存在，则创建新用户
            user = createUserWithPhone(phone);
        }

        // 在session中保存用户
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(8));
        user.setPhone(phone);
        return user;
    }
}
