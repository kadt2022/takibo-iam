package com.takibo.adp.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
                "com.takibo.adp.spring",
                "com.takibo.adp.test"
        }
)
public class AdpTestApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(AdpTestApplication.class, args);
    }
}
