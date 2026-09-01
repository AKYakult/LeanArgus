package com.example.myargus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.myargus.**.mapper")
public class MyArgusApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyArgusApplication.class, args);
    }

}
