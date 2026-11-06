package com.his;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({"com.his.system.mapper", "com.his.audit.mapper", "com.his.emr.mapper",
        "com.his.loop.mapper", "com.his.cdss.mapper"})
public class HisApplication {

    public static void main(String[] args) {
        SpringApplication.run(HisApplication.class, args);
    }
}
