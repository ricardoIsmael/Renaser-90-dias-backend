package com.renaser.os.habits.application.ports.out.diario;

import com.renaser.os.habits.domain.model.diario.EntradaDiario;
import com.renaser.os.habits.domain.model.diario.TipoEntradaDiario;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Optional;

public interface LoadEntradaDiarioPort {

    Optional<EntradaDiario> porParticipanteFechaYTipo(UserId participanteId, LocalDate fecha, TipoEntradaDiario tipo);
}
