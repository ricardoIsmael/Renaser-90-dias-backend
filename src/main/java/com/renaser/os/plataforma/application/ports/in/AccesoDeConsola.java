package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.shared.domain.UserId;

import java.util.Objects;

/**
 * Quién usa la consola Soporte TI y con qué la desbloqueó (plan.md D6).
 *
 * @param tokenDeConsola el de {@code X-Consola-Token}; sin él, la consola está bloqueada (423)
 * @param sesion         el id de la sesión del pedido: los tokens de la consola valen solo en la sesión que los pidió
 */
public record AccesoDeConsola(UserId actor, String tokenDeConsola, String sesion) {

    public AccesoDeConsola {
        Objects.requireNonNull(actor, "actor es obligatorio");
    }
}
