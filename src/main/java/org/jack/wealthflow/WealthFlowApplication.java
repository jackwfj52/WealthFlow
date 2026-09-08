package org.jack.wealthflow;

import org.jack.wealthflow.config.PortGuard;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("org.jack.wealthflow.mapper")
public class WealthFlowApplication {

    public static void main(String[] args) {
        PortGuard.releaseOccupiedPort();
        SpringApplication.run(WealthFlowApplication.class, args);
    }
}