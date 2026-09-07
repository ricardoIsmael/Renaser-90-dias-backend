package com.renaser.os.habits.application.ports.out.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoadPreferenciaHorarioPort {

    Optional<PreferenciaHorario> porParticipanteYHabito(UserId participanteId, HabitoId habitoId);

    /** UNA sola consulta para N habitos de un mismo participante — proyecciones de lectura (hueco #10). */
    List<PreferenciaHorario> porParticipanteYHabitos(UserId participanteId, Collection<HabitoId> habitoIds);
    /** Preferencia efectiva en la fecha: excepcion puntual > cambio general vigente > preferencia general. */
    Optional<PreferenciaHorario> porParticipanteHabitoYFecha(UserId participanteId, HabitoId habitoId,
                                                           LocalDate fecha);

    List<PreferenciaHorario> porParticipanteHabitosYFecha(UserId participanteId, Collection<HabitoId> habitoIds,
                                                        LocalDate fecha);

    List<HabitoId> habitosConHorarioEntre(UserId participanteId, LocalDate desde, LocalDate hasta);

    /**
     * Los habitos que el aprendiz APAGO para esa fecha (V38). Va por separado y no dentro de
     * {@link #porParticipanteHabitosYFecha} a proposito: "a que hora va" y "si va" son dos
     * preguntas distintas, y la segunda ya tiene su lugar en el generador junto a la pausa y al
     * dia de desbloqueo. Meterla en `PreferenciaHorario` habria obligado a todos sus lectores a
     * conocer un campo que a la mayoria no le importa.
     */
    List<HabitoId> habitosApagadosEn(UserId participanteId, LocalDate fecha);
}
