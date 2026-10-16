package com.driving.exam.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 驾考智能辅导系统 - 后端启动类。
 *
 * <p>scanBasePackages 扩大到 com.driving.exam，确保能扫描到 common 模块
 * （GlobalExceptionHandler 等）与 pojo 模块中的组件。</p>
 */
@SpringBootApplication(scanBasePackages = "com.driving.exam")
@MapperScan("com.driving.exam.server.mapper")
public class DrivingExamApplication {

    public static void main(String[] args) {
        SpringApplication.run(DrivingExamApplication.class, args);
    }
}
