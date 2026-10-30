package com.renaser.os.habits.application.ports.out.diario;

import com.renaser.os.habits.domain.model.diario.EntradaDiario;
import com.renaser.os.habits.domain.model.diario.TipoEntradaDiario;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoadEntradaDiarioPort {

    Optional<EntradaDiario> porParticipanteFechaYTipo(UserId participanteId, LocalDate fecha, TipoEntradaDiario tipo);

    /** Las entradas de UN tipo entre dos fechas (ambas inclusive), por fecha (spec 017). Vacia si no hay; nunca null. */
    List<EntradaDiario> entreFechasDeTipo(UserId participanteId, TipoEntradaDiario tipo, LocalDate desde, LocalDate hasta);
}
