package org.example.bitlygood;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BitlyGoodApplication {

    public static void main(String[] args) {
        SpringApplication.run(BitlyGoodApplication.class, args);
    }

}
