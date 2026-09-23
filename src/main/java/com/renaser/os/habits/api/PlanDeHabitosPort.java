package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * El plan de habitos del aprendiz visto desde otro modulo: que habitos lleva, cuales puede pausar
 * y que dia de esta semana puede elegir para los de eleccion semanal (2026-09-23).
 *
 * <p>Primer consumidor: las herramientas {@code proponer_pausar_habito} y
 * {@code proponer_dia_de_habito_semanal} del acompanante de {@code rag} (fase 2, D-153). Se expone
 * aca por la regla de siempre (D-41, regla 01): {@code rag} no lee {@code desbloqueos_habito} ni
 * {@code dias_semanales_habito} por su cuenta, y las escrituras pasan por los MISMOS casos de uso
 * que {@code PATCH /api/v1/habit-unlocks/{habitId}} y {@code PUT /api/v1/weekly-habit-days/{habitId}},
 * con todas sus guardas.
 *
 * <p>Ningun tipo de {@code habits.domain} cruza esta frontera. Las escrituras lanzan las mismas
 * excepciones que esos casos de uso ({@code IllegalStateException} si el habito es obligatorio,
 * {@code NoSuchElementException} si no esta en su plan o no existe, {@code NotAuthorizedException}
 * si la cuenta esta suspendida o es el Dia 0, {@code IllegalArgumentException} si el dia no se
 * puede elegir): traducirlas a un texto es del llamador.
 */
public interface PlanDeHabitosPort {

    /**
     * Llamada de modulo a modulo ya autorizada por quien invoca; igual rechaza a un suspendido.
     * "Hoy" se resuelve en la zona del participante, nunca con la fecha del servidor (E-91).
     *
     * @throws java.util.NoSuchElementException si no tiene participacion en el programa
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si la cuenta esta suspendida
     */
    PlanDeHabitos planDe(UserId participanteId);

    /**
     * Pausa un habito de su plan. Delega en {@code CambiarEstadoHabitoDelPlanUseCase}.
     *
     * @param hastaInclusive ultimo dia de la pausa en su zona; {@code null} = sin fecha de fin
     */
    void pausar(UserId actorId, UUID habitoId, LocalDate hastaInclusive);

    /** Reactiva un habito pausado de su plan. Delega en {@code CambiarEstadoHabitoDelPlanUseCase}. */
    void reactivar(UserId actorId, UUID habitoId);

    /** Elige el dia de esta semana de un habito semanal. Delega en {@code ElegirDiaSemanalUseCase}. */
    void elegirDiaSemanal(UserId actorId, UUID habitoId, LocalDate fecha);

    /**
     * @param hoy       el dia de hoy en la zona del participante
     * @param habitos   los habitos activos de su plan ({@code desbloqueos_habito})
     * @param semanales los habitos de eleccion semanal que puede ver
     */
    record PlanDeHabitos(LocalDate hoy, List<HabitoDelPlan> habitos, List<HabitoSemanal> semanales) {
    }

    /**
     * @param obligatorio  no se puede pausar ({@code habitos.desactivable = false}, V18)
     * @param pausadoHoy   pausado HOY segun {@code DesbloqueoHabito.estaPausadoEl}
     * @param pausadoHasta ultimo dia de la pausa registrada; {@code null} si es indefinida o no hay
     */
    record HabitoDelPlan(UUID habitoId, String titulo, boolean obligatorio, boolean pausadoHoy,
                         LocalDate pausadoHasta) {
    }

    /**
     * @param diaElegido    el dia ya elegido esta semana, o {@code null}
     * @param diasElegibles los dias de esta semana que todavia se pueden elegir, en orden; vacio
     *                      en el Dia 0
     */
    record HabitoSemanal(UUID habitoId, String titulo, LocalDate diaElegido, List<LocalDate> diasElegibles) {
    }
}
