package com.tc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tc.redis.pojo.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;

@SpringBootTest
class RedisStringTemplateTests {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        ops.set("name", "王五");
        Object name = ops.get("name");
        System.out.println(name);

    }

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testSaveUser() throws JsonProcessingException {


        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        User user = new User("张三", 25);

        String json = mapper.writeValueAsString(user);

        ops.set("user:100", json);

        String jsonUser = ops.get("user:100");

        User user1 = mapper.readValue(jsonUser, User.class);
        System.out.println(user1);
    }
    @Test
    void testHash(){
        HashOperations<String, Object, Object> ops = stringRedisTemplate.opsForHash();
        ops.put("user:200","name","新木优子");
        ops.put("user:200","age","23");
        Map<Object, Object> map = ops.entries("user:200");
        System.out.println(map);
    }
}
