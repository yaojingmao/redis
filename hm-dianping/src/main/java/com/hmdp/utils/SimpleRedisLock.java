package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;
    public static final String KEY_PREFIX = "lock:";
    public static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static  final DefaultRedisScript<Long> defaultScript;
    static {
        defaultScript=new DefaultRedisScript<>();
        defaultScript.setResultType(Long.class);
        defaultScript.setLocation(new ClassPathResource("unlock.lua"));
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeOutSec) {
//        对线程id的信息进行复杂化，因为两个jvm有可能线程id一致所以在前面拼了个UUID
        String ThreadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, ThreadId, timeOutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);


    }

    @Override

    public void unLock() {
        String ThreadId = ID_PREFIX + Thread.currentThread().getId();
        stringRedisTemplate.execute(defaultScript, Collections.singletonList(KEY_PREFIX + name),ThreadId);
        }
    }


/*    public void unLock() {
        String ThreadId = ID_PREFIX + Thread.currentThread().getId();
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        防止同一个用户一个线程删除另一个线程的锁，判断存在redis中的线程id是否和自己的线程id一致，一致则删除
        if (ThreadId.equals(id)) {

            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }*/

