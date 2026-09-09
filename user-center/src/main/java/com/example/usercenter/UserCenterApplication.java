package com.example.usercenter;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@SpringBootApplication
@MapperScan("com.example.usercenter.mapper")
@ServletComponentScan

public class UserCenterApplication {

    public static void main(String[] args) {

        SpringApplication.run(UserCenterApplication.class, args);
    }

}
