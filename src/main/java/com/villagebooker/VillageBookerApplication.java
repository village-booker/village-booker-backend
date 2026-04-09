package com.villagebooker;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication
public class VillageBookerApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(VillageBookerApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("---------------------------------");
        System.out.println("RADI! Java " + System.getProperty("java.version") + " i Spring su podignuti.");
        System.out.println("---------------------------------");
    }
}
