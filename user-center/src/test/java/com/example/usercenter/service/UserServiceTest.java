package com.example.usercenter.service;

import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.domain.request.UserRegisterRequest;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class UserServiceTest {

    @Resource
    private UserService userService;

//    @Test
//    void testAddUser() {
//        User user = new User();
//
//        user.setUsername("dogyi");
//        user.setUserAccount("123");
//        user.setAvatarUrl(
//                "https://thirdwx.qlogo.cn/mmopen/vi_32/huZNG8yAnLjxuEHlrwTo9GIlnsjbR0RRZuvzeZ9QAxzveBnhWhjDEUt9OZqmrBXuu8nM5BqNYOYX80h8bjM0WmOLJPrBLNC6aY8LRNfgAqY/132");
//        user.setGender(0);
//        user.setUserPassword("12345678");
//        user.setPhone("123");
//        user.setEmail("4565");
//
//        boolean result = userService.save(user);
//        System.out.println(user.getId());
//        Assertions.assertTrue(result);
//
//    }
//
//    @Test
//    void userRegister() {
//        // 测试参数为空的情况
//        UserRegisterRequest request1 = new UserRegisterRequest();
//        request1.setUserAccount("");
//        request1.setUserPassword("12345678");
//        request1.setCheckPassword("12345678");
//        long result1 = userService.userRegister(request1);
//        Assertions.assertEquals(-1, result1, "参数为空时应该返回-1");
//
//        // 测试账户长度不足4位
//        UserRegisterRequest request2 = new UserRegisterRequest();
//        request2.setUserAccount("123"); // 少于4位
//        request2.setUserPassword("12345678");
//        request2.setCheckPassword("12345678");
//        long result2 = userService.userRegister(request2);
//        Assertions.assertEquals(-1, result2, "账户长度不足4位时应该返回-1");
//
//        // 测试密码长度不足8位
//        UserRegisterRequest request3 = new UserRegisterRequest();
//        request3.setUserAccount("testuser");
//        request3.setUserPassword("1234567"); // 少于8位
//        request3.setCheckPassword("1234567");
//        long result3 = userService.userRegister(request3);
//        Assertions.assertEquals(-1, result3, "密码长度不足8位时应该返回-1");
//
//        // 测试账户包含特殊字符
//        UserRegisterRequest request4 = new UserRegisterRequest();
//        request4.setUserAccount("test@user"); // 包含特殊字符@
//        request4.setUserPassword("12345678");
//        request4.setCheckPassword("12345678");
//        long result4 = userService.userRegister(request4);
//        Assertions.assertEquals(-1, result4, "账户包含特殊字符时应该返回-1");
//
//        // 测试密码和确认密码不一致
//        UserRegisterRequest request5 = new UserRegisterRequest();
//        request5.setUserAccount("testuser");
//        request5.setUserPassword("12345678");
//        request5.setCheckPassword("12345679"); // 与密码不一致
//        long result5 = userService.userRegister(request5);
//        Assertions.assertEquals(-1, result5, "密码和确认密码不一致时应该返回-1");
//
//        // 测试正常注册情况
//        UserRegisterRequest request6 = new UserRegisterRequest();
//        request6.setUserAccount("testuser" + System.currentTimeMillis()); // 使用时间戳确保唯一性
//        request6.setUserPassword("12345678");
//        request6.setCheckPassword("12345678");
//        request6.setUsername("测试用户");
//        long result6 = userService.userRegister(request6);
//        Assertions.assertTrue(result6 > 0, "正常注册应该返回大于0的用户ID");
//
//        // 测试重复账户注册
//        UserRegisterRequest request7 = new UserRegisterRequest();
//        request7.setUserAccount(request6.getUserAccount()); // 使用相同的账户
//        request7.setUserPassword("12345678");
//        request7.setCheckPassword("12345678");
//        long result7 = userService.userRegister(request7);
//        Assertions.assertEquals(-1, result7, "重复账户注册应该返回-1");
//    }

}