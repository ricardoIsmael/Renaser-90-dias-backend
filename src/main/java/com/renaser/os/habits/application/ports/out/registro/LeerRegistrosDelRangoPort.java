package com.renaser.os.habits.application.ports.out.registro;

import com.renaser.os.habits.api.RegistrosDeHabitoFinder.RegistroDeHabito;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * Los registros de hábito de UNA persona en un rango de fechas con el instante en que se cumplió cada uno: una sola
 * consulta sobre {@code registros_dia_idx} ({@code participante_id, fecha_ejecucion}).
 */
public interface LeerRegistrosDelRangoPort {

    List<RegistroDeHabito> deParticipanteEntre(UserId participanteId, LocalDate desde, LocalDate hasta);
}
