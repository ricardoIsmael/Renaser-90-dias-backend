package com.renaser.os.habits.application.ports.out.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CambioHorarioPendiente;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoadCambioHorarioPendientePort {

    Optional<CambioHorarioPendiente> porParticipanteYHabito(UserId participanteId, HabitoId habitoId);

    /** Todos los cambios programados de un participante — UNA consulta, para la vista de configuracion. */
    List<CambioHorarioPendiente> deParticipante(UserId participanteId);

    /**
     * Los que ya llego su dia ({@code fecha_efectiva <= fecha}) — de TODOS los participantes, porque el
     * barrido nocturno que los promueve no va por participante. Mismo criterio que
     * {@link CambioHorarioPendiente#rigeEn(LocalDate)}, resuelto en una sola consulta.
     */
    List<CambioHorarioPendiente> queYaRigenEn(LocalDate fecha);
}
