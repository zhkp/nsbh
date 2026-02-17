package com.kp.nsbh;

import com.kp.nsbh.config.NsbhProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(NsbhProperties.class)
@EnableScheduling
public class NsbhApplication {
    public static void main(String[] args) {
        SpringApplication.run(NsbhApplication.class, args);
    }
}
