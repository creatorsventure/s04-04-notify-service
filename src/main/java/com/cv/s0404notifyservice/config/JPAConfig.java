package com.cv.s0404notifyservice.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = {
        "com.cv.s0402notifyservicepojo.entity"
})
@EnableJpaRepositories("com.cv.s0404notifyservice.repository")
public class JPAConfig {
}
