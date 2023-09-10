package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate=stringRedisTemplate;
    }
    @Override
    public boolean preHandle (HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token=request.getHeader("authorization");
        String userStr=stringRedisTemplate.opsForValue().get("logintoken:"+token);
        UserDTO userDTO= JSONObject.parseObject(userStr, UserDTO.class);
        UserHolder.saveUser(userDTO);
        stringRedisTemplate.expire("logintoken:"+token,1, TimeUnit.HOURS);
        return true;
    }


}
