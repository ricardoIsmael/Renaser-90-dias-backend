package com.renaser.os.habits.application.ports.out.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.AjusteDeAlarma;
import com.renaser.os.habits.domain.model.preferencia.HorarioPorFecha;
import com.renaser.os.habits.domain.model.preferencia.HorarioSemanal;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.preferencia.RecordatorioDelDia;
import com.renaser.os.shared.domain.UserId;

import java.time.DayOfWeek;
import java.time.LocalDate;

public interface SavePreferenciaHorarioPort {

    PreferenciaHorario save(PreferenciaHorario preferencia);

    void saveParaFecha(HorarioPorFecha horario);

    /** Borra la excepcion de esa fecha: el dia vuelve a regirse por lo general. Idempotente. */
    void borrarParaFecha(UserId participanteId, HabitoId habitoId, LocalDate fecha);

    /**
     * Fija la hora (y el apagado) de un habito para UN dia de la semana, todas las semanas (V39, V40).
     *
     * <p>Spec 008 (FR-013): si {@code horario.recordatorio()} es {@code null} NO toca el aviso propio que
     * ese dia ya tenga; guardar la hora desde un APK anterior no puede borrarlo. Para poner o quitar el
     * aviso esta {@link #fijarRecordatorioParaDiaSemana}. Spec 009: lo mismo con {@code horario.alarma()},
     * que se pone o se quita con {@link #fijarAlarmaParaDiaSemana}.
     */
    void saveParaDiaSemana(UserId participanteId, HabitoId habitoId, HorarioSemanal horario);

    /**
     * Pone el aviso propio de ese dia de la semana CONSERVANDO su hora (spec 008). {@code null} lo devuelve
     * a heredar el general; si la fila queda encendida sin hora ni aviso propios, se borra. Idempotente.
     */
    void fijarRecordatorioParaDiaSemana(UserId participanteId, HabitoId habitoId, DayOfWeek diaSemana,
                                        RecordatorioDelDia recordatorio);

    /**
     * Pone como suena la alarma de ese dia de la semana CONSERVANDO su hora y su aviso (spec 009, D-283).
     * {@code null} o un ajuste vacio la devuelve a heredar la general; si la fila queda encendida sin hora,
     * aviso ni alarma propios, se borra. Idempotente. Una fila de solo alarma cuenta para
     * {@code LoadPreferenciaHorarioPort#habitosConEsquemaPorDia}: es una fila.
     */
    void fijarAlarmaParaDiaSemana(UserId participanteId, HabitoId habitoId, DayOfWeek diaSemana,
                                  AjusteDeAlarma alarma);

    /** Borra la hora de ese dia: vuelve a regirse por el horario general. Idempotente. */
    void borrarParaDiaSemana(UserId participanteId, HabitoId habitoId, DayOfWeek diaSemana);
}
