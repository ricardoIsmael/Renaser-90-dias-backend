package com.renaser.os;

import org.springframework.boot.SpringApplication;

/** Arranca la app en local con el Postgres de Testcontainers ya conectado. */
public class TestRenaserOsApplication {

    public static void main(String[] args) {
        SpringApplication.from(RenaserOsApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
