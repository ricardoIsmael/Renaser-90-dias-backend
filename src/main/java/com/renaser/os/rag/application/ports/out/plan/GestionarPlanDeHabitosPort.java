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

    /**
     * Igual que el interruptor de Plan: {@code habits} agrega el habito al plan si no estaba y lo pausa.
     *
     * @param hastaInclusive {@code null} = pausa sin fecha de fin
     */
    void pausar(UserId actorId, UUID habitoId, LocalDate hastaInclusive);

    void reactivar(UserId actorId, UUID habitoId);

    void elegirDiaSemanal(UserId actorId, UUID habitoId, LocalDate fecha);

    /**
     * Como se llama cada habito para la persona y para el programa, su descripcion y si se puede
     * renombrar ({@code consultar_como_se_hace_habito} y {@code proponer_renombrar_habito}, D-236).
     *
     * @throws RuntimeException si no hay participacion o la cuenta esta suspendida
     */
    List<FichaDeHabito> fichasDe(UserId participanteId);

    /** El mismo caso de uso que {@code PUT /api/v1/habits/{habitId}/rename}, con sus guardas. */
    void renombrar(UserId actorId, UUID habitoId, String tituloPersonal, String motivo);

    /** El mismo caso de uso que {@code DELETE /api/v1/habits/{habitId}/rename}. */
    void quitarRenombre(UserId actorId, UUID habitoId);

    /**
     * @param hoy     el dia de hoy en la zona del participante, resuelto por {@code habits}
     * @param habitos TODOS los habitos que ve, con los obligatorios marcados: cualquiera que no sea
     *                obligatorio se puede pausar, igual que en Plan (D-165, E-245)
     */
    record PlanDelAprendiz(LocalDate hoy, List<HabitoDelPlan> habitos, List<HabitoSemanal> semanales) {

        public Optional<HabitoDelPlan> habitoDelPlan(UUID habitoId) {
            return habitos.stream().filter(habito -> habito.habitoId().equals(habitoId)).findFirst();
        }

        public Optional<HabitoSemanal> habitoSemanal(UUID habitoId) {
            return semanales.stream().filter(habito -> habito.habitoId().equals(habitoId)).findFirst();
        }

        /** Los que no se apagan ningun dia ni se pausan (V18). */
        public List<HabitoDelPlan> obligatorios() {
            return habitos.stream().filter(HabitoDelPlan::obligatorio).toList();
        }
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

    /**
     * @param tituloDelPrograma el titulo del programa (catalogo, o el que eligio al crear un habito propio)
     * @param tituloPersonal    el nombre que le puso con el renombre, o {@code null} si no lo renombro
     * @param descripcion       la del catalogo, o {@code null}; a veces solo dice que evidencia se manda
     * @param renombrable       si el renombre lo acepta (hoy, solo las dos bebidas)
     */
    record FichaDeHabito(UUID habitoId, String tituloDelPrograma, String tituloPersonal, String descripcion,
                         boolean renombrable) {

        /** Como lo ve la persona en la app: su nombre propio si le puso uno. */
        public String tituloVisible() {
            return tituloPersonal != null ? tituloPersonal : tituloDelPrograma;
        }

        public boolean renombrado() {
            return tituloPersonal != null;
        }
    }
}
