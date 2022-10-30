package com.tc.test;

import com.tc.jedis.util.JedisConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import java.util.Map;

public class JedisPoolTest {
    private Jedis jedis;


    @BeforeEach
    void setUp(){
         jedis = JedisConnectionFactory.getJedis();
        jedis.select(0);
    }

    @Test
    void testString(){
         jedis.set("name", "张三");
         jedis.set("age", "23");
        String s = jedis.get("name");
        System.out.println(s);
    }
    @Test
    void testHash(){
        jedis.hset("user:1","name","里斯");
        jedis.hset("user:1","age","58");
        Map<String, String> map = jedis.hgetAll("user:1");
        System.out.println(map);

    }

    @AfterEach
    void tearDown() {
        if (jedis!=null){
            jedis.close();
        }
    }
}
