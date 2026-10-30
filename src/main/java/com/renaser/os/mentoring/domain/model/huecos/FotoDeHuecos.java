package com.renaser.os.mentoring.domain.model.huecos;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * La foto de una semana ya terminada de una persona: los huecos tal como se calcularon al cerrar su domingo, con el
 * horario que regía entonces (spec 015, FR-018). Es historia: se guarda una vez y no se reescribe (constitución IV).
 *
 * <p>Las horas de los hábitos se sobrescriben cuando la persona las cambia, y no queda registro de qué hora regía en
 * una semana ya vivida. Sin esta foto, una semana pasada se recapitularía con el horario de hoy.
 *
 * @param zona     la zona en la que se midieron los días; la de la persona al cerrar
 * @param cerradaEn el instante en que se guardó
 */
public record FotoDeHuecos(UserId participante, ZoneId zona, SemanaDeHuecos semana, Instant cerradaEn) {

    public FotoDeHuecos {
        Objects.requireNonNull(participante, "participante es obligatorio");
        Objects.requireNonNull(zona, "zona es obligatoria");
        Objects.requireNonNull(semana, "semana es obligatoria");
        Objects.requireNonNull(cerradaEn, "cerradaEn es obligatoria");
    }
}
