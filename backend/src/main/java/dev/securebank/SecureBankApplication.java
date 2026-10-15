package dev.securebank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class SecureBankApplication {
    @org.springframework.context.annotation.Bean
    java.time.Clock riskClock() { return java.time.Clock.systemUTC(); }
    public static void main(String[] args) {
        SpringApplication.run(SecureBankApplication.class, args);
    }
}
