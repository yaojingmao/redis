package com.tc.redis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/*
* 默认的redisTemplate 使用的是 JdkSerializationRedisSerializer jdk序列化器会将键和值变成 \xac\xed\x00\x05t\x00\x04name
* 值变成\xAC\xED\x00\x05t\x00	\xE7\x8E\x8B\xE8\x80\x81\xE4\xBA\x94 耗内存且不易辨别，所以自己重新定义序列化器
* 如下指定键用StringRedisSerializer value用GenericJackson2JsonRedisSerializer，但是value会携带一个class 的对象字节码
* 它的作用是自动反序列化时知道变成那个对象，耗内存。所以采用key 和value 都使用StringRedisSerializer，StringRedisTemplate就是
* 这种效果key 和value 都使用StringRedisSerializer，只不过要自己将对象序列化成json，json反序列化成对象，降低了
* 内存消耗
* GenericJackson2JsonRedisSerializer
* StringRedisSerializer 字符串序列化器
* */


@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String,Object> redisTemplate(RedisConnectionFactory redisConnectionFactory){
//       创建一个redisTemplate对象
        RedisTemplate redisTemplate=new RedisTemplate();
//        设置连接工厂
        redisTemplate.setConnectionFactory(redisConnectionFactory);
//        设置序列化工具
        GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer=new GenericJackson2JsonRedisSerializer();
//          key和hashKey都是使用String类型的序列化工具
        redisTemplate.setKeySerializer(RedisSerializer.string());
        redisTemplate.setHashKeySerializer(RedisSerializer.string());
//          value和hashValue都是使用json序列化工具
        redisTemplate.setValueSerializer(genericJackson2JsonRedisSerializer);
        redisTemplate.setHashValueSerializer(genericJackson2JsonRedisSerializer);

        return redisTemplate;



    }
}
