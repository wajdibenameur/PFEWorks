package tn.iteam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication

@EnableAsync
public class PfeprojectApplication {

    public static void main(String[] args) {
        SpringApplication.run(PfeprojectApplication.class, args);
    }

}
