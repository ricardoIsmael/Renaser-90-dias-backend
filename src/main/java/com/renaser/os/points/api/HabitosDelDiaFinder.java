package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * Contrato publico de `points` para leer, desde `habits`, un resumen liviano de los tracks
 * de hoy de un participante (gap #21 PLAN_INTEGRACION_FRONTEND.md, {@code GET /home}).
 * Vive en `points.api` (no en `habits.api`) por el mismo motivo documentado en
 * {@link PorcentajeRocasFinder}: `habits` ya depende de `points` para otorgar puntos, asi
 * que `points` no puede depender de `habits` en la otra direccion sin crear un ciclo que
 * Spring Modulith rechaza — DIP, el consumidor declara lo que necesita, el proveedor
 * (`habits.application.services.HabitosDelDiaFinderService`) lo implementa.
 *
 * <p>Reutiliza puertas adentro la misma proyeccion "sin N+1" que ya usa la pantalla de
 * habitos ({@code ConsultarTracksDelDiaUseCase}) — esta interfaz es una fachada mas liviana
 * sobre esos mismos datos, no una consulta nueva.
 */
public interface HabitosDelDiaFinder {

    /**
     * Tracks del participante para {@code fecha}, en el orden en que los devuelve la
     * consulta interna.
     *
     * <p><b>Decision de diseno:</b> a diferencia de los casos de uso internos de `habits`
     * (que piden {@code actorId} para {@code requireSelf}), este finder no lo pide: es una
     * llamada de modulo a modulo ya autorizada por quien invoca (el caller ya establecio
     * que {@code participanteId} es el usuario autenticado, o que tiene permiso para verlo).
     * Puertas adentro se invoca la logica existente con {@code actorId == participanteId}
     * para satisfacer trivialmente el autoservicio, lo que trae aparejado el mismo chequeo
     * de progreso/suspension que ya hace {@code ConsultarTracksDelDiaUseCase} — si el
     * participante no tiene {@code ProgresoParticipanteHabits} (no es aprendiz activo) o
     * esta suspendido, este metodo propaga la misma excepcion que ese caso de uso
     * ({@code NoSuchElementException}/{@code NotAuthorizedException}). El llamador debe
     * manejarlo igual que ya documenta FEATURE_HOME.md para las fallas parciales de sus
     * widgets.
     *
     * @return lista vacia si no hay tracks generados para ese dia — nunca null
     */
    List<HabitoDelDiaResumen> deHoy(UserId participanteId, LocalDate fecha);
}
