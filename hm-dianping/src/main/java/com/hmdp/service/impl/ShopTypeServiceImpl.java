package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryCache() {
        String key = CACHE_SHOP_TYPE_KEY;

        // 查询redis中是否存在，存在则直接返回
        String shopTypeListJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopTypeListJson)) {
            List<ShopType> shopList = JSONUtil.toList(shopTypeListJson,ShopType.class);
            return Result.ok(shopList);
        }

        // 如果在redis中不存在，去数据库中查询
        QueryChainWrapper<ShopType> shops = query();
        List<ShopType> shopTypeList = shops.orderByAsc("sort").list();

        // 如果数据库中没有
        if (shops == null) {
            return Result.fail("店铺不存在");
        }

        // 如果数据库中有则写入redis缓存，设置时间
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypeList), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 返回数据
        return Result.ok(shopTypeList);
    }
}
