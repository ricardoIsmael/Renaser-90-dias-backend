package com.renaser.os.habits.application.ports.in.preferencia;

import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.EditarPreferenciaHorarioCommand;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.ResultadoEdicionPreferencia;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.UserId;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Spec 048 (FR-019, fase 3): planificar el horario, el aviso y la alarma de un hábito SIN RED, con la hora real en que se hizo. Mismas
 * reglas que con red (la ventana «ya arrancó», la cuota semanal y la semana libre se miden a esa hora) y la regla de choques D13: si la fila que
 * toca cambió en el servidor después de lo que hiciste, gana el servidor. Un hábito que ya no existe es {@code RECURSO_YA_NO_EXISTE}; un
 * {@code DELETE} sobre algo que ya no está se aplica sin más. Cada método responde lo mismo que su equivalente con red.
 *
 * <p>Todos pueden terminar en un {@link com.renaser.os.shared.domain.NoAplicadoSinRedException} (409 con {@code codigo}).
 */
public interface PreferenciasSinRedUseCase {

    ResultadoEdicionPreferencia editar(EditarPreferenciaHorarioCommand command, HechoSinRed sinRed);

    void cambiarEstadoDelDia(UserId actor, HabitoId habito, LocalDate fecha, boolean activo, HechoSinRed sinRed);

    void fijarDiaDeLaSemana(UserId actor, HabitoId habito, DayOfWeek dia, LocalTime horaDisparo, LocalTime horaLimite, HechoSinRed sinRed);

    void fijarAvisoDelDia(UserId actor, HabitoId habito, DayOfWeek dia, boolean activo, List<Integer> antelaciones, HechoSinRed sinRed);

    void fijarAlarma(UserId actor, HabitoId habito, CombinacionDeAlarma combinacion, HechoSinRed sinRed);

    void fijarAlarmaDelDia(UserId actor, HabitoId habito, DayOfWeek dia, CombinacionDeAlarma combinacion, HechoSinRed sinRed);

    void apagarDiaDeLaSemana(UserId actor, HabitoId habito, DayOfWeek dia, HechoSinRed sinRed);

    void quitarDiaDeLaSemana(UserId actor, HabitoId habito, DayOfWeek dia, HechoSinRed sinRed);
}
