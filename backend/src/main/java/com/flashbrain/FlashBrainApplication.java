package com.flashbrain;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.flashbrain.mapper")
@SpringBootApplication
public class FlashBrainApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlashBrainApplication.class, args);
    }
}
