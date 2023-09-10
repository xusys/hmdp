package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexPatterns;
import com.hmdp.utils.RegexUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode (String phone, HttpSession session) {
        if(RegexUtils.isPhoneInvalid(phone))
        {
            return Result.fail("INVALID");
        }
        String code= RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set("logincdode:"+phone,code,2, TimeUnit.MINUTES);
        log.debug(code);
        return Result.ok();
    }

    @Override
    public Result login (LoginFormDTO loginForm, HttpSession session) {
            String phone=loginForm.getPhone();
            if(RegexUtils.isPhoneInvalid(loginForm.getPhone()))
            {
                 return Result.fail("INVALID");
            }
           String cacheCode=stringRedisTemplate.opsForValue().get("logincdode:"+phone);
            //Object cacheCode=session.getAttribute("code");
            String code=loginForm.getCode();
            if(cacheCode==null||!cacheCode.equals(code))
            {
                 return Result.fail("CODEWRONG");
            }
           User user= query().eq("phone",phone).one();
            if(user==null)
            {
                user=createUserWithPhone(phone);
            }
            String token= UUID.randomUUID().toString();
            UserDTO userDTO=BeanUtil.copyProperties(user, UserDTO.class);
            stringRedisTemplate.opsForValue().set("logintoken:"+token, JSON.toJSONString(userDTO));
            stringRedisTemplate.expire("logintoken:"+token,1,TimeUnit.HOURS);
//            Map<String,Object>userMap=BeanUtil.beanToMap(userDTO);
//            stringRedisTemplate.opsForHash().putAll("logintoken:"+token,userMap);
//            stringRedisTemplate.expire("logintoken:"+token,1,TimeUnit.HOURS);
            //session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
            return Result.ok(token);
    }

    private User createUserWithPhone (String phone) {
        User user=new User();
        user.setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
