package com.renaser.os.calendar.domain.model.evento;

import java.time.Instant;

/**
 * Una ocurrencia concreta ya expandida por {@link ExpansorOcurrencias}.
 *
 * @param inicioOcurrencia el slot ORIGINAL de la serie (clave estable para RSVP/excepciones,
 *                         nunca cambia aunque la ocurrencia se reprograme)
 * @param iniciaEn         el instante EFECTIVO en que arranca (= inicioOcurrencia, salvo
 *                         que una excepcion la haya movido)
 * @param duracionMinutos  la duracion efectiva (override o la del evento)
 * @param titulo           no nulo SOLO cuando una excepcion retitulo esta ocurrencia
 */
public record Ocurrencia(Instant inicioOcurrencia, Instant iniciaEn, Integer duracionMinutos, String titulo) {
}
