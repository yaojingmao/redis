package com.tc.jedis.util;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisConnectionFactory {
    private static final JedisPool jedisPool;

    static {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        //最大连接数，池子里最多创建8个连接
        jedisPoolConfig.setMaxTotal(8);
        //最大空闲连接数
        jedisPoolConfig.setMaxIdle(8);
        //最小空闲连接数
        jedisPoolConfig.setMinIdle(0);
        //如果没有连接了最多等待1秒，在这一秒内有则成功，没有则失败
        jedisPoolConfig.setMaxWaitMillis(1000);

//参数 配置类 ip port 超时时间 密码
        jedisPool = new JedisPool(jedisPoolConfig, "192.168.139.130", 6379, 1000, "123321");
    }

    public static Jedis getJedis() {
        return jedisPool.getResource();
    }

}
