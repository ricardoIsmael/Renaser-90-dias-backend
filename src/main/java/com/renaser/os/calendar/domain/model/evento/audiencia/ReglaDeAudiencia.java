package com.renaser.os.calendar.domain.model.evento.audiencia;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que se guarda de cada parte que NO es una persona suelta (tabla {@code reglas_audiencia_evento}): las que se leen en cada momento (grupo, alumnos
 * de un mentor, mentores, todo el personal) y la de cada color, que guarda cuándo se tomó su foto. Las personas que entraron por color viven en
 * {@link Invitado}.
 *
 * @param grupoId     solo {@link TipoDeParte#GRUPO}
 * @param mentorId    solo {@link TipoDeParte#MIS_ALUMNOS}: de quién son los alumnos (quien creó el evento; se guarda por si después lo edita otro)
 * @param color       solo {@link TipoDeParte#COLOR}
 * @param resueltoEn  solo {@link TipoDeParte#COLOR}: el instante de la foto (decisión P1)
 */
public record ReglaDeAudiencia(TipoDeParte tipo, UUID grupoId, UserId mentorId, ColorDeAudiencia color, Instant resueltoEn) {

    public ReglaDeAudiencia {
        if (tipo == TipoDeParte.PERSONA) {
            throw new IllegalArgumentException("Una persona suelta es un Invitado, no una regla");
        }
    }

    public static ReglaDeAudiencia grupo(UUID grupoId) {
        return new ReglaDeAudiencia(TipoDeParte.GRUPO, grupoId, null, null, null);
    }

    public static ReglaDeAudiencia alumnosDe(UserId mentor) {
        return new ReglaDeAudiencia(TipoDeParte.MIS_ALUMNOS, null, mentor, null, null);
    }

    public static ReglaDeAudiencia mentores() {
        return new ReglaDeAudiencia(TipoDeParte.MENTORES, null, null, null, null);
    }

    public static ReglaDeAudiencia todoElPersonal() {
        return new ReglaDeAudiencia(TipoDeParte.PERSONAL, null, null, null, null);
    }

    public static ReglaDeAudiencia color(ColorDeAudiencia color, Instant resueltoEn) {
        return new ReglaDeAudiencia(TipoDeParte.COLOR, null, null, color, resueltoEn);
    }
}
