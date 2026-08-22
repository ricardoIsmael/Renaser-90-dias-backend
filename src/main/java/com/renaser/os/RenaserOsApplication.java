package com.renaser.os;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Unico punto de arranque del monolito modular (CLAUDE.MD §4.3).
 * Cada subpaquete directo de com.renaser.os es un modulo Spring Modulith.
 */
@SpringBootApplication
public class RenaserOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(RenaserOsApplication.class, args);
    }
}
