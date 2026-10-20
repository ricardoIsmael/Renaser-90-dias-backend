package com.renaser.os.habits.application.ports.out.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.HorarioSemanal;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.UserId;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LoadPreferenciaHorarioPort {

    Optional<PreferenciaHorario> porParticipanteYHabito(UserId participanteId, HabitoId habitoId);

    /** UNA sola consulta para N habitos de un mismo participante — proyecciones de lectura (hueco #10). */
    List<PreferenciaHorario> porParticipanteYHabitos(UserId participanteId, Collection<HabitoId> habitoIds);
    /** Preferencia efectiva en la fecha: excepcion puntual > cambio general vigente > preferencia general. */
    Optional<PreferenciaHorario> porParticipanteHabitoYFecha(UserId participanteId, HabitoId habitoId,
                                                           LocalDate fecha);

    /**
     * Igual que {@link #porParticipanteHabitoYFecha}, en lote. Spec 008 (D-282): la capa del dia de la
     * semana pisa POR CAMPO, la hora si la fila trae hora y el aviso (activo, antelaciones, minutos) si
     * trae aviso propio, de modo que una fila de solo aviso deja la hora de abajo.
     */
    List<PreferenciaHorario> porParticipanteHabitosYFecha(UserId participanteId, Collection<HabitoId> habitoIds,
                                                        LocalDate fecha);

    /**
     * Lo mismo que {@link #porParticipanteHabitosYFecha} pero SIN el aviso propio del dia de la semana
     * (la hora de ese dia si cuenta). Es lo que muestra el listado de preferencias como aviso GENERAL del
     * habito: el APK anterior lo lee y reescribe como el general, y el editor nuevo lo necesita para
     * saber que heredan los dias sin aviso propio. Con aviso por dia, mezclarlo haria que el general
     * "cambiara" segun el dia que se consulta.
     */
    List<PreferenciaHorario> porParticipanteHabitosYFechaSinAvisoDelDia(UserId participanteId,
                                                                        Collection<HabitoId> habitoIds,
                                                                        LocalDate fecha);

    /** Las filas de dia de la semana de varios habitos en UNA consulta (hora, apagado y aviso propios). */
    List<HorarioSemanal> horariosSemanalesDe(UserId participanteId, Collection<HabitoId> habitoIds);

    /**
     * Los habitos que tienen AL MENOS UNA fila de dia de la semana, sea por hora propia, aviso propio o dia
     * apagado: los que usan el esquema por dia (spec 008, R6). El push de inicio lleva la etiqueta del dia
     * solo para ellos, la misma regla con la que la app decide su esquema de alarmas.
     */
    Set<HabitoId> habitosConEsquemaPorDia(UserId participanteId, Collection<HabitoId> habitoIds);

    List<HabitoId> habitosConHorarioEntre(UserId participanteId, LocalDate desde, LocalDate hasta);

    /**
     * Los habitos que el aprendiz APAGO para esa fecha (V38). Va por separado y no dentro de
     * {@link #porParticipanteHabitosYFecha} a proposito: "a que hora va" y "si va" son dos
     * preguntas distintas, y la segunda ya tiene su lugar en el generador junto a la pausa y al
     * dia de desbloqueo. Meterla en `PreferenciaHorario` habria obligado a todos sus lectores a
     * conocer un campo que a la mayoria no le importa.
     */
    List<HabitoId> habitosApagadosEn(UserId participanteId, LocalDate fecha);

    /**
     * Los siete dias de un habito con hora propia por dia de semana (V39). Solo devuelve los dias
     * que TIENEN fila: los demas se rigen por el horario general, y decir eso es del que lee.
     */
    List<HorarioSemanal> horarioSemanalDe(UserId participanteId, HabitoId habitoId);

    /**
     * Los habitos que el aprendiz APAGO para ese dia de la semana, todas las semanas (V40).
     *
     * Hermano de {@link #habitosApagadosEn(UserId, LocalDate)}, que es por fecha y no se repite.
     * Los dos responden "va hoy?" y el generador los junta en el mismo conjunto de descarte.
     */
    List<HabitoId> habitosApagadosEnDiaSemana(UserId participanteId, DayOfWeek diaSemana);
}
