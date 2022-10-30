package com.tc;

import com.tc.redis.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SpringBootTest
class SpringDataRedisDemoApplicationTests {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void contextLoads() {
        ValueOperations ops = redisTemplate.opsForValue();
        ops.set("name", "王老五");
        Object name = ops.get("name");
        System.out.println(name);

    }

    @Test
    void testSaveUser() {


        ValueOperations ops = redisTemplate.opsForValue();
        ops.set("user:100", new User("张三", 25));
        System.out.println(ops.get("user:100"));
    }

}
