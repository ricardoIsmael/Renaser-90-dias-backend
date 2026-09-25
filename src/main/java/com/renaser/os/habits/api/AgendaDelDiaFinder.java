package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Lo que otro modulo puede preguntarle y pedirle a `habits` sobre el dia de un aprendiz.
 *
 * <p>Primer consumidor: las herramientas del agente conversacional de `rag` (2026-09-05). Se
 * expone como {@code api/} y no se deja que el agente consulte {@code registros_habito} por su
 * cuenta, por la misma razon de siempre (D-41, regla 01): las reglas del dia de un habito — que
 * dia es "hoy" para esa persona, cuanto paga, cuando vence, quien puede completarlo — viven en
 * este modulo y en ninguno mas.
 *
 * <p><b>No pide fecha, a proposito.</b> "Hoy" se resuelve puertas adentro en la zona del
 * participante (E-91/E-105): si el llamador pudiera pasar una fecha, tarde o temprano alguien
 * pasaria la del servidor y volveriamos a tener el bug de zona en un modulo distinto.
 */
public interface AgendaDelDiaFinder {

    /**
     * Los habitos de hoy del aprendiz, en el orden en que los devuelve la proyeccion interna.
     * Lista vacia si no tiene ninguno — nunca null.
     *
     * <p>Llamada de modulo a modulo ya autorizada por quien invoca, mismo criterio documentado en
     * {@code points.HabitosDelDiaFinder}: no se pide un {@code actorId} aparte porque el llamador
     * ya establecio que puede operar en nombre de ese participante.
     */
    List<HabitoEnJuegoResumen> deHoyDe(UserId participanteId);

    /**
     * Marca un habito como completado en nombre del propio aprendiz y devuelve los puntos que
     * efectivamente se otorgaron.
     *
     * <p>Pasa por el MISMO caso de uso que usa la app ({@code CompletarRegistroUseCase}), con
     * todas sus guardas: pertenencia, estado terminal, ventana vencida, politicas de habitos
     * especiales (Santuario, post en el Muro) y el bloqueo pesimista contra doble cobro. Un
     * agente no tiene ningun atajo que un aprendiz no tenga.
     *
     * @param actorId el propio participante — el caso de uso rechaza cualquier otro
     */
    int completar(UserId actorId, UUID registroId);

    /**
     * La zona en la que {@code habits} resolvio el "hoy" y los plazos de {@link #deHoyDe}
     * (2026-09-23). Existe para que el llamador pueda decir esos instantes en la hora local del
     * aprendiz, o traducir una hora local suya ("si lo hago a las 21:00") a un instante, con la
     * MISMA zona con que se calcularon — no con otra fuente que pudiera discrepar.
     *
     * <p>Mismo respaldo que la proyeccion del dia: si el participante no tiene progreso, UTC.
     */
    ZoneId zonaDe(UserId participanteId);
}
