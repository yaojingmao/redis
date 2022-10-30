package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDto;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        1.获取session
//        HttpSession session = request.getSession();
//       1. 获取请求头中的token
        String token = request.getHeader("authorization");
//        2.从session中获取用户信息
//        Object user = session.getAttribute("user");
//        判断token是否为空 ，若为空直接拦截
        if (StrUtil.isBlank(token)) {
            return true;
        }
        Map<Object, Object> mapUser = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);


//        3.判断有无用户信息
        if (mapUser.isEmpty()) {
//        4.没有则拦截
           return true;
        }
//        4.1将查询到的map对象转换成UserDto对象
        UserDto userDto = BeanUtil.fillBeanWithMap(mapUser, new UserDto(), false);


//        5.保存用户到threadLocal中
        UserHolder.saveUser(userDto);
//        6.刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }



    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();

    }
}
