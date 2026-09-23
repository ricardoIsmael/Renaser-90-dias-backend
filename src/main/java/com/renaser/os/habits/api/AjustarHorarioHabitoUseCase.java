package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Las escrituras de horario del propio aprendiz, para otro modulo (2026-09-23).
 *
 * <p>Primer consumidor: las herramientas de escritura del acompanante de {@code rag} (fase 4 de
 * {@code docs/arquitectura/PROPUESTA_ACOMPANANTE_90_DIAS.md}), que ejecutan esto recien cuando la
 * persona toca "Confirmar" (D-153). Se expone aca, y no se deja que {@code rag} escriba
 * {@code preferencias_horario} por su cuenta, por la regla de siempre (D-41, regla 01).
 *
 * <p><b>No es una segunda implementacion.</b> Cada metodo delega en el MISMO caso de uso que la
 * app usa por HTTP ({@code PATCH /habit-preferences/{habitId}}, {@code PATCH …/days/{date}},
 * {@code PUT/DELETE …/weekdays/{weekday}}): la cuota semanal ({@code CuotaEdicionHorario}), "el dia
 * en curso no se reacomoda" (D-91), la regla de los obligatorios (V18) y la ventana del dia (D-122)
 * se aplican adentro, igual que para la app. El acompanante no tiene cuota propia (§5.0).
 *
 * <p>Ningun tipo de {@code habits.domain} cruza esta frontera. Los rechazos del negocio llegan
 * como las excepciones estandar que documenta cada metodo, las mismas que la app ve como 4xx.
 */
public interface AjustarHorarioHabitoUseCase {

    /**
     * Cambia la hora de un habito: con {@code fecha}, solo ese dia futuro; sin ella, el horario
     * general desde manana (en la zona del participante). Consume la cuota semanal. Conserva el
     * recordatorio que ya tenia la persona: el acompanante no lo toca.
     *
     * @throws java.util.NoSuchElementException si el habito o el participante no existen
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si la cuenta esta suspendida o el
     *                                                             habito personal es de otra persona
     * @throws IllegalArgumentException si la fecha no es futura, la hora de inicio no cabe en el dia
     *                                  o el habito no esta activo
     * @throws IllegalStateException si ya no le queda cupo de cambios esa semana de programa
     */
    CambioDeHorarioAplicado cambiarHorario(CambioDeHorario cambio);

    /**
     * Apaga ({@code activo = false}) o vuelve a encender UN dia concreto. No consume cuota. Encender
     * borra el apagado POR FECHA; un apagado por dia de semana no se toca.
     *
     * @throws java.util.NoSuchElementException si el habito o el participante no existen
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si la cuenta esta suspendida
     * @throws IllegalArgumentException si la fecha ya paso
     * @throws IllegalStateException si se quiere apagar un habito obligatorio
     */
    void cambiarEstadoDelDia(UserId participanteId, EstadoDelDia estado);

    /**
     * Fija la hora de un dia de la semana, todas las semanas. Consume la cuota semanal, medida en la
     * proxima ocurrencia de ese dia.
     *
     * @throws java.util.NoSuchElementException si el habito o el participante no existen
     * @throws com.renaser.os.shared.domain.NotAuthorizedException si la cuenta esta suspendida
     * @throws IllegalArgumentException si la hora de inicio no cabe en el dia
     * @throws IllegalStateException si ya no le queda cupo de cambios esa semana de programa
     */
    void fijarDiaDeLaSemana(HorarioDeDiaDeLaSemana horario);

    /**
     * Apaga el habito ese dia de la semana, todas las semanas. No consume cuota.
     *
     * @throws java.util.NoSuchElementException si el habito no existe
     * @throws IllegalStateException si el habito es obligatorio
     */
    void apagarDiaDeLaSemana(UserId participanteId, UUID habitoId, DayOfWeek diaSemana);

    /**
     * Quita la hora propia o el apagado de ese dia de la semana: vuelve al horario general.
     * Idempotente.
     *
     * @throws java.util.NoSuchElementException si el habito no existe
     */
    void quitarDiaDeLaSemana(UserId participanteId, UUID habitoId, DayOfWeek diaSemana);

    /**
     * @param horaLimite {@code null} = sin hora limite propia (rige la del catalogo, si tiene)
     * @param fecha      {@code null} = cambio general desde manana
     */
    record CambioDeHorario(UserId participanteId, UUID habitoId, LocalTime horaInicio, LocalTime horaLimite,
                           LocalDate fecha) {
    }

    /**
     * Lo que quedo guardado y la cuota despues del cambio (la misma que devuelve el PATCH).
     *
     * @param rigeDesde la fecha desde la que rige (la pedida, o manana si fue un cambio general)
     */
    record CambioDeHorarioAplicado(LocalTime horaInicio, LocalTime horaLimite, LocalDate rigeDesde,
                                   int cambiosUsados, int cambiosRestantes, int cambiosLimite,
                                   boolean semanaDeAcomodoLibre) {
    }

    /** @param activo {@code false} apaga ese dia; {@code true} lo vuelve a lo normal. */
    record EstadoDelDia(UUID habitoId, LocalDate fecha, boolean activo) {
    }

    /** @param horaLimite {@code null} = ese dia hereda la hora limite general */
    record HorarioDeDiaDeLaSemana(UserId participanteId, UUID habitoId, DayOfWeek diaSemana, LocalTime horaInicio,
                                  LocalTime horaLimite) {
    }
}
