package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDto;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
//        1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
//        2.不符合返回错误信息
            return Result.fail("手机号格式不正确");
        }

//        3.符合生成验证码,使用的是hutool里面随机数生成
        String code = RandomUtil.randomNumbers(6);
//        4.将验证码存储到session中
//        session.setAttribute("code", code);
//        4.1将验证码存储到redis中
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        5.发送验证码
        log.debug("验证码为：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        1.校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
//        3.不符合返回错误信息
            return Result.fail("手机号格式不正确");
        }
//        2.校验验证码
//        Object catchCode = session.getAttribute("code");
        String catchCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        if (catchCode == null || !catchCode.equals(loginForm.getCode())) {
//        3.不符合返回错误信息
            return Result.fail("验证码不正确");
        }
//        4.一致，根据手机号查询用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, loginForm.getPhone());
        User user = baseMapper.selectOne(wrapper);
//        5.没查到，创建用户保存到session中
        if (user == null) {
            user = createUserWithPhone(loginForm.getPhone());
        }
//        6.查到，直接保存到session中
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDto.class));
//        6.随机生成一个token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
//        6.1将用户对象转换成hashmap
        Map<String, Object> mapUser = BeanUtil.beanToMap(user,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fileName,fileValue)->fileValue.toString()));
//        6.2 保存用户信息到redis中
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY+ token,mapUser);
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY,RedisConstants.LOGIN_USER_TTL,TimeUnit.MINUTES);
//        6.3返回token
        return Result.ok(token);
    }

    //根据手机号创建一个user对象
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
