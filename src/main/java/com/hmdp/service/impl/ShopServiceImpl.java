package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    CacheClient cacheClient;
    private static  final ExecutorService executor= Executors.newFixedThreadPool(10);
    @Override
    public Result queryById (Long id) {
        Shop shop=cacheClient.queryWithLogicalExpire("shopcache:",id,Shop.class,this::getById,30L,TimeUnit.MINUTES);
        //Shop shop=cacheClient.queryWithPassThrough("shopcache:",id,Shop.class,this::getById,30L,TimeUnit.MINUTES);
        if(shop==null)
        {
            return Result.fail("not exist");
        }
        return Result.ok(shop);
    }
    public Shop queryWithLogicalExpire(Long id){
        String shopStr=stringRedisTemplate.opsForValue().get("shopcache:"+id);
        if(StrUtil.isBlank(shopStr))//查找缓存
        {
            return null;//未命中
        }
        RedisData redisData=JSON.parseObject(shopStr,RedisData.class);
        JSONObject data=(JSONObject) redisData.getData();
        Shop shop=JSON.parseObject(String.valueOf(data),Shop.class);
       // Shop shop=(Shop)redisData.getData();
        LocalDateTime expireTime=redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now()))
        {
            return shop;
        }
        try {
            boolean isLock=tryLock("lock:shop:"+id);
            if(isLock)
            {
                executor.submit(()->{
                    this.saveShopRedis(id,20L);
                });
            }
        }catch (Exception e)
        {
            throw new RuntimeException(e);
        }finally {
            unLock("lock:shop:"+id);
        }
        return shop;
    }
    public Shop queryWithMutex(Long id){
        String shopStr=stringRedisTemplate.opsForValue().get("shopcache:"+id);
        if(StrUtil.isNotBlank(shopStr))//查找缓存
        {
            Shop shop= JSON.parseObject(shopStr,Shop.class);
            return shop;
        }
        if(shopStr!=null)//缓存穿透
        {
            return null;
        }
        Shop shop= null;//查找数据库
        try {
            Boolean flag=tryLock("lock:shop:"+id);//获取互斥锁
            if(!flag)
            {
                Thread.sleep(50);//等待
                return queryWithMutex(id);
            }
            shop = getById(id);
            if(shop==null)//设置空缓存
            {
                stringRedisTemplate.opsForValue().set("shopcache:"+id,"",2,TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set("shopcache:"+id,JSON.toJSONString(shop),30, TimeUnit.MINUTES);//写缓存
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        finally {
            unLock("lock:shop:"+id);//释放互斥锁
        }
        return shop;
    }

    @Override
    @Transactional
    public Result update (Shop shop) {
        Long id=shop.getId();
        if(id==null)
        {
            return Result.fail("idnull");
        }
        updateById(shop);
        stringRedisTemplate.delete("shopcache:"+id);
        return Result.ok();
    }


    private boolean tryLock(String key){
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
    @Override
    public  void saveShopRedis(Long id,Long expireSeconds){
        Shop shop=getById(id);
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set("shopcache:"+id,JSON.toJSONString(redisData));
    }

}
