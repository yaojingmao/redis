package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 唐总
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
@Resource
private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
//调用缓存穿透代码
//        Shop shop = queryWithPassThrough(id);
        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        System.out.println(shop);
//        互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
//        Shop shop = queryWithLogicalExpire(id);
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("商铺信息id不能为空");
        }
//        先更新数据库
        updateById(shop);
//        删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }



    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }


    //    获取锁
    private Boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        直接返回会拆箱，如果包装类为null则会导致空指针，涉及到自动拆装箱的语法糖
        return BooleanUtil.isTrue(flag);
    }

    //    释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    //    缓存穿透代码
    public Shop queryWithPassThrough(Long id) {
        //1. 从redis获取缓存
        String cacheShopInfo = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//2.判断缓存是否命中
        if (StrUtil.isNotBlank(cacheShopInfo)) {
//3.命中则直接返回商铺信息

            Shop shop = JSONUtil.toBean(cacheShopInfo, Shop.class);
            return shop;
        }
//3.1命中判断是否为空值
        if (cacheShopInfo != null) {
            return null;
        }
//4.未命中则根据id查询数据库
        Shop shop = getById(id);
//5.判断商铺是否存在
        if (shop == null) {
            //6.不存在返回404
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
//7.存在将商铺信息写入redis中
        String shopJson = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//8.返回商铺信息
        return shop;
    }

    //    缓存击穿代码
    public Shop queryWithMutex(Long id) {
        //1. 从redis获取缓存
        String cacheShopInfo = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//2.判断缓存是否命中
        if (StrUtil.isNotBlank(cacheShopInfo)) {
//3.命中则直接返回商铺信息

            Shop shop = JSONUtil.toBean(cacheShopInfo, Shop.class);
            return shop;
        }
//3.1命中判断是否为空值
        if (cacheShopInfo != null) {
            return null;
        }
        Shop shop = null;
        try {
//    3.2实现缓存重建
//    3.3获取互斥锁
            Boolean isLock = tryLock(LOCK_SHOP_KEY + id);
//    3.4判断是否获取锁成功
            if (!isLock) {
                //    3.5失败则休眠
                Thread.sleep(50);
                return queryWithMutex(id);
            }
//    3.6成功查询数据库


//4.未命中则根据id查询数据库
            shop = getById(id);
//5.判断商铺是否存在
            if (shop == null) {
                //6.不存在返回404
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
//7.存在将商铺信息写入redis中
            String shopJson = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7.1释放互斥锁
            unLock(LOCK_SHOP_KEY + id);
        }

        //8.返回商铺信息
        return shop;
    }

    //逻辑过期代码
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);
    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
//        查缓存是否有商铺信息，没有则直接返回null
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        要返回shop对象但我们在redisdata是使用的Object，所以要传一个类型
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
//        判断逻辑过期时间是在当前时间之后
        if (expireTime.isAfter(LocalDateTime.now())) {
            //没过期
            return shop;
        }
//        过期进行缓存重建，先获取锁
        Boolean lock = tryLock(LOCK_SHOP_KEY + id);
        if (lock) {
//            让另一个线程进行缓存重建
            executorService.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
//                    释放锁
                    unLock(LOCK_SHOP_KEY + id);
                }
            });
        }
//        返回旧的数据信息
        return shop;
    }

    public void saveShop2Redis(Long id, Long expireSeconds) {
        //根据id查询数据库信息
        Shop shop = getById(id);

        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
}
