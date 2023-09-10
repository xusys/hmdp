package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hmdp.entity.Shop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.TimeoutUtils;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.events.Event;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
public class CacheClient {
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    private static ExecutorService executorService= Executors.newFixedThreadPool(10);
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(value),time,unit);
    }
    public  void  setWithLogicalExpire(String key,Object value,Long time ,TimeUnit unit) {
        RedisData redisData=new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));
        redisData.setData(value);
        stringRedisTemplate.opsForValue().set(key,JSON.toJSONString(redisData));
    }
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R>dbFallback, Long time, TimeUnit unit){
        String key=keyPrefix+id;
        String json=stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(json)){
            return JSON.parseObject(json,type);
        }
        if(json!=null){
            return null;
        }
        R r=dbFallback.apply(id);
        if(r==null){
            stringRedisTemplate.opsForValue().set(key,"",1,TimeUnit.MINUTES);
            return null;
        }
        this.set(key,r,time,unit);
        return r;
    }
    private boolean tryLock(String key){
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
    public <R,ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID,R>dbFallback, Long time, TimeUnit unit){
        String key=keyPrefix+id;
        String json=stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isBlank(json))//查找缓存
        {
            return null;//未命中
        }
        RedisData redisData=JSON.parseObject(json,RedisData.class);
        JSONObject data=(JSONObject) redisData.getData();
        R r=JSON.parseObject(String.valueOf(data),type);
        // Shop shop=(Shop)redisData.getData();
        LocalDateTime expireTime=redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now()))
        {
            return r;
        }
        try {
            boolean isLock=tryLock("lock:shop:"+id);
            if(isLock)
            {
               executorService.submit(()->{
                    R r1=dbFallback.apply(id);
                    this.setWithLogicalExpire(key,r1,time,unit );
                });
            }
        }catch (Exception e)
        {
            throw new RuntimeException(e);
        }finally {
            unLock("lock:shop:"+id);
        }
        return r;
    }


}
