package com.dpworld.fms.api;
import org.springframework.boot.*; import org.springframework.boot.autoconfigure.*; import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages="com.dpworld.fms") @EnableScheduling public class DpwFmsApplication { public static void main(String[] args){SpringApplication.run(DpwFmsApplication.class,args);} }
