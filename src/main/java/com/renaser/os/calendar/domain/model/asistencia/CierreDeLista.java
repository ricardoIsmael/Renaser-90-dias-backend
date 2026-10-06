package com.renaser.os.calendar.domain.model.asistencia;

import java.time.Instant;
import java.util.Objects;

import com.renaser.os.shared.domain.UserId;

/**
 * Una lista cerrada (tabla {@code listas_asistencia_evento}). Que no exista = la lista está abierta.
 *
 * @param cerradaPor {@code null} si esa cuenta ya se borró
 */
public record CierreDeLista(Instant cerradaEn, UserId cerradaPor) {

    public CierreDeLista {
        Objects.requireNonNull(cerradaEn, "cerradaEn es obligatorio");
    }
}
