package com.renaser.os.habits.application.ports.out.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.HorarioPorFecha;
import com.renaser.os.habits.domain.model.preferencia.HorarioSemanal;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.shared.domain.UserId;

import java.time.DayOfWeek;
import java.time.LocalDate;

public interface SavePreferenciaHorarioPort {

    PreferenciaHorario save(PreferenciaHorario preferencia);

    void saveParaFecha(HorarioPorFecha horario);

    /** Borra la excepcion de esa fecha: el dia vuelve a regirse por lo general. Idempotente. */
    void borrarParaFecha(UserId participanteId, HabitoId habitoId, LocalDate fecha);

    /** Fija la hora de un habito para UN dia de la semana, todas las semanas (V39). */
    void saveParaDiaSemana(UserId participanteId, HabitoId habitoId, HorarioSemanal horario);

    /** Borra la hora de ese dia: vuelve a regirse por el horario general. Idempotente. */
    void borrarParaDiaSemana(UserId participanteId, HabitoId habitoId, DayOfWeek diaSemana);
}
