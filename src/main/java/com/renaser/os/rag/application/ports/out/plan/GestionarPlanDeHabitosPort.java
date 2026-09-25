package com.renaser.os.rag.application.ports.out.plan;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto propio de {@code rag} para leer y cambiar el plan de habitos del aprendiz: pausar o
 * reactivar un habito, y elegir el dia de esta semana de un habito semanal (herramientas
 * {@code proponer_pausar_habito} y {@code proponer_dia_de_habito_semanal}, fase 2, D-153).
 *
 * <p>Las tablas son de {@code habits}: el adaptador delega en {@code habits.api.PlanDeHabitosPort}
 * (D-41). Mismo criterio que {@code ConsultarHorariosPort}: records propios, el contrato ajeno no
 * llega a {@code rag.application}.
 *
 * <p>Las escrituras SOLO las llaman las {@code AccionConfirmable}, cuando la persona toca
 * "Confirmar". Lanzan las excepciones del negocio ({@code IllegalStateException},
 * {@code NoSuchElementException}, {@code NotAuthorizedException}, {@code IllegalArgumentException});
 * traducirlas a un texto legible es de quien llama.
 */
public interface GestionarPlanDeHabitosPort {

    /** @throws RuntimeException si no hay participacion o la cuenta esta suspendida */
    PlanDelAprendiz planDe(UserId participanteId);

    /** @param hastaInclusive {@code null} = pausa sin fecha de fin */
    void pausar(UserId actorId, UUID habitoId, LocalDate hastaInclusive);

    void reactivar(UserId actorId, UUID habitoId);

    void elegirDiaSemanal(UserId actorId, UUID habitoId, LocalDate fecha);

    /**
     * @param hoy          el dia de hoy en la zona del participante, resuelto por {@code habits}
     * @param obligatorios los que no se apagan ningun dia ni se pausan (V18). Son de la base del
     *                     programa y casi nunca estan en {@code habitos}, que son los que se suman al
     *                     plan (D-165)
     */
    record PlanDelAprendiz(LocalDate hoy, List<HabitoDelPlan> habitos, List<HabitoSemanal> semanales,
                           List<HabitoObligatorio> obligatorios) {

        public Optional<HabitoDelPlan> habitoDelPlan(UUID habitoId) {
            return habitos.stream().filter(habito -> habito.habitoId().equals(habitoId)).findFirst();
        }

        public Optional<HabitoSemanal> habitoSemanal(UUID habitoId) {
            return semanales.stream().filter(habito -> habito.habitoId().equals(habitoId)).findFirst();
        }

        public Optional<HabitoObligatorio> obligatorio(UUID habitoId) {
            return obligatorios.stream().filter(habito -> habito.habitoId().equals(habitoId)).findFirst();
        }
    }

    /** Un habito obligatorio del programa, con el titulo que ve el aprendiz. */
    record HabitoObligatorio(UUID habitoId, String titulo) {
    }

    /**
     * @param obligatorio  no se puede pausar
     * @param pausadoHoy   hoy no le toca porque esta pausado
     * @param pausadoHasta ultimo dia de la pausa registrada, o {@code null}
     */
    record HabitoDelPlan(UUID habitoId, String titulo, boolean obligatorio, boolean pausadoHoy,
                         LocalDate pausadoHasta) {
    }

    /**
     * @param diaElegido    el dia ya elegido esta semana, o {@code null}
     * @param diasElegibles los dias de esta semana que todavia se pueden elegir
     */
    record HabitoSemanal(UUID habitoId, String titulo, LocalDate diaElegido, List<LocalDate> diasElegibles) {
    }
}
