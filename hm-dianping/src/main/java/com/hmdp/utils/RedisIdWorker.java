package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    //2022年1月1号的秒数
    private static final Long startTime = 1640995200L;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
//    向左移动32位
    private static final int COUNT_BITS = 32;

    public long nextId(String keyPrefix) {
//    1.生成时间戳
//        生成订单的当前时间
        LocalDateTime now = LocalDateTime.now();
//        将当前时间转换为秒数，参数为时区
        long nowTime = now.toEpochSecond(ZoneOffset.UTC);
//        获得距离2022年1月1号的秒数
        long timeStamp = nowTime - startTime;


//    2.生成唯一序列号
//        使用年月日的格式后期可以好统计一天的订单数量或一个月订单数量
        String data = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
//        如果没有这个key那么会自动创建这个key默认值为0
        Long count = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + ":" + data);

//    3.返回唯一Id

//   将时间向右移动32位 为序列号提供位置  设计规则是 1个符号位 31个时间戳的位置，32个序列号的位置
        return timeStamp << COUNT_BITS | count;
    }


}
