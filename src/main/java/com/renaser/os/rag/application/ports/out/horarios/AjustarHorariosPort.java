package com.renaser.os.rag.application.ports.out.horarios;

import com.renaser.os.shared.domain.UserId;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Puerto propio de {@code rag} para ESCRIBIR el horario del aprendiz (fase 4 del acompanante,
 * 2026-09-23). Solo lo llaman los {@code AccionConfirmable} de horario, es decir, cuando la persona
 * ya toco "Confirmar" (D-153): el modelo nunca llega hasta aca.
 *
 * <p>Las tablas son de {@code habits}: el adaptador delega en {@code habits.api.AjustarHorarioHabitoUseCase}
 * (D-41), que a su vez corre los mismos casos de uso que la app — cuota semanal, dias pasados,
 * obligatorios. Mismo criterio que {@link ConsultarHorariosPort}: records propios, nada del
 * contrato ajeno en {@code rag.application}.
 *
 * <p>Los rechazos del negocio suben como las excepciones estandar que documenta
 * {@code AjustarHorarioHabitoUseCase} ({@code IllegalStateException}: sin cupo u obligatorio;
 * {@code IllegalArgumentException}: fecha u hora no editable; {@code NoSuchElementException};
 * {@code NotAuthorizedException}). Quien llama las traduce a un {@code Fallo} legible.
 */
public interface AjustarHorariosPort {

    HorarioCambiado cambiarHorario(UserId participanteId, CambioDeHorario cambio);

    /** @param activo {@code false} apaga ese dia; {@code true} quita el apagado de esa fecha */
    void cambiarEstadoDelDia(UserId participanteId, UUID habitoId, LocalDate fecha, boolean activo);

    void fijarDiaDeLaSemana(UserId participanteId, HorarioSemanal horario);

    void apagarDiaDeLaSemana(UserId participanteId, UUID habitoId, DayOfWeek diaSemana);

    void quitarDiaDeLaSemana(UserId participanteId, UUID habitoId, DayOfWeek diaSemana);

    /**
     * @param horaLimite {@code null} = sin hora limite propia
     * @param fecha      {@code null} = cambio general desde manana
     */
    record CambioDeHorario(UUID habitoId, LocalTime horaInicio, LocalTime horaLimite, LocalDate fecha) {
    }

    record HorarioCambiado(LocalTime horaInicio, LocalTime horaLimite, LocalDate rigeDesde, int cambiosRestantes,
                           int cambiosLimite, boolean semanaDeAcomodoLibre) {
    }

    /** @param horaLimite {@code null} = ese dia hereda la hora limite general */
    record HorarioSemanal(UUID habitoId, DayOfWeek diaSemana, LocalTime horaInicio, LocalTime horaLimite) {
    }
}
