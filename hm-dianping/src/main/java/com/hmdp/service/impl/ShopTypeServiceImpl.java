package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public List<ShopType> shopTypeList() {
        String key = "shop_type";
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopTypeJson)) {
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeJson, ShopType.class);
            return shopTypeList;
        }
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypeList));
        return shopTypeList;


    }
}
