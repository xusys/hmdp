package com.hmdp.service.impl;

import com.alibaba.fastjson.JSON;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    StringRedisTemplate stringRedisTemplate;
    @Override
    public List<ShopType> queryAll () {
        List<String>list=stringRedisTemplate.opsForList().range("shoptypecache",0,-1);
        List<ShopType>res=new ArrayList<>();
        if(!list.isEmpty())
        {

            for(int i=0;i<list.size();i++)
            {
                res.add(JSON.parseObject(list.get(i),ShopType.class));
            }
            return res;
        }
        res=query().orderByAsc("sort").list();

        for(int i=0;i< res.size();i++)
        {
            stringRedisTemplate.opsForList().rightPush("shoptypecache",JSON.toJSONString(res.get(i)));
        }
        return res;
    }
}
