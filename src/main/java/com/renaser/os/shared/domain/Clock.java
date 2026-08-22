package com.renaser.os.shared.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Puerto de reloj (CLAUDE.MD §5). El dominio NUNCA llama a Instant.now() directamente:
 * sin esto es imposible testear reglas de "dia N del programa" sin esperar al calendario real.
 *
 * Ojo: se llama Clock a proposito, igual que en §5.1. Dentro de domain/ nunca se importa
 * java.time.Clock, asi que no hay ambiguedad.
 */
public interface Clock {

    Instant now();

    LocalDate today();
}
