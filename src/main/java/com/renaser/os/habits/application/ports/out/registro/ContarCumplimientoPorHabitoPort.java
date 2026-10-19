package com.renaser.os.habits.application.ports.out.registro;

import com.renaser.os.habits.domain.model.registro.CumplimientoDeUnHabito;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * Consulta de UNA persona hábito por hábito (D-277): en UNA sola consulta a {@code registros_habito}, los
 * conteos crudos de cada hábito con al menos un registro en [desde, hasta]. Sin registros, lista vacía.
 */
public interface ContarCumplimientoPorHabitoPort {

    List<CumplimientoDeUnHabito> deParticipanteEntre(UserId participante, LocalDate desde, LocalDate hasta);
}
