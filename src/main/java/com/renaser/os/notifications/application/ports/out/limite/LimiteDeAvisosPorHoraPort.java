package com.renaser.os.notifications.application.ports.out.limite;

import java.time.Instant;
import java.util.UUID;

/**
 * Un tope de avisos por hora, compartido por todas las instancias (spec 002: «como máximo N push por hora» para el
 * circuito de la IA, que puede oscilar). Hoy es Redis.
 */
public interface LimiteDeAvisosPorHoraPort {

    /**
     * Dice si el aviso {@code id} del {@code grupo}, en el instante {@code en}, entra en el tope: si en la hora que
     * termina en {@code en} salieron menos de {@code maximo} avisos del grupo. Si entra, queda anotado como salido; un
     * aviso omitido no ocupa lugar. Idempotente por {@code id}: un aviso que ya salió vuelve a dar {@code true}.
     */
    boolean admite(String grupo, UUID id, Instant en, int maximo);
}
