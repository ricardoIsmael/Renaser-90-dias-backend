package com.renaser.os.rag.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * El análisis 80/20 de una semana o un mes de una persona quedó listo (spec 031, FR-024). {@code notifications} lo escucha y le avisa al
 * teléfono, siempre con un texto fijo.
 *
 * <p><b>Nunca lleva el análisis ni lo que la persona escribió</b>: el contenido es dato personal y viaja por la API, donde se revalidan
 * los permisos, no por un aviso. Va por evento y no llamando a {@code notifications} a mano, por el mismo motivo que
 * {@link PatronDeMalestarRepetidoEvent}: {@code rag} no sabe que existe una bandeja, y el outbox corre al listener después del commit.
 *
 * @param claveDeDeduplicacion el id del análisis: un reintento del outbox no crea un segundo aviso
 * @param periodo              {@code SEMANA} o {@code MES}
 * @param inicio               el primer día del periodo, para abrir la hoja en él
 */
public record AnalisisListoEvent(UUID claveDeDeduplicacion, UUID personaId, String periodo, LocalDate inicio, Instant generadoEn) {
}
