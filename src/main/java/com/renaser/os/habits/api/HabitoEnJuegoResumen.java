package com.renaser.os.habits.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Un habito del dia visto desde afuera de `habits`, con lo que hace falta para hablar de el:
 * como se llama, en que estado esta, cuanto paga y hasta cuando.
 *
 * <p>Nace para las herramientas del agente conversacional (2026-09-05), pero no es un tipo "del
 * agente": es la proyeccion publica que a `habits` le faltaba. {@code points.HabitoDelDiaResumen}
 * ya existia y se queda como esta — solo lleva {@code trackId}/{@code titulo}/{@code estado},
 * porque {@code GET /home} no necesita mas. Este agrega lo que aquel no tiene (puntos y plazo) en
 * vez de ensancharlo, para no cambiarle el contrato a un consumidor que no lo pidio.
 *
 * @param estado         espejo de {@code EstadoRegistro} como String — mismo criterio que
 *                       {@code HabitoDelDiaResumen.estado}: no filtrar un tipo interno fuera del
 *                       {@code @NamedInterface}
 * @param puntosEnJuego  lo que paga completarlo ahora, o {@code null} si ya esta en estado
 *                       terminal (no hay nada en juego en lo que ya se hizo o se perdio)
 * @param puntosMaximos  el techo de la escala, para poder decir "6 de 10" sin conocer la constante
 * @param plazo          instante en que se bloquea, o {@code null} si el habito no vence
 */
public record HabitoEnJuegoResumen(UUID registroId, String titulo, String estado, Integer puntosEnJuego,
                                    Integer puntosMaximos, Instant plazo) {
}
