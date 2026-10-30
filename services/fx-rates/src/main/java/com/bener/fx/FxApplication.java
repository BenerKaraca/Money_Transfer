package com.bener.fx;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class FxApplication {
    public static void main(String[] args) { SpringApplication.run(FxApplication.class, args); }
    @Bean Clock clock() { return Clock.systemUTC(); }
}
