package com.renaser.os.habits.application.ports.out.registro;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoadRegistroHabitoPort {

    Optional<RegistroHabito> byId(RegistroHabitoId id);

    /** Version con bloqueo para el camino de escritura: evita que dos requests concurrentes
     * completen el mismo registro y otorguen puntos dos veces. */
    Optional<RegistroHabito> byIdParaEscritura(RegistroHabitoId id);

    Optional<RegistroHabito> porParticipanteHabitoYFecha(UserId participanteId, HabitoId habitoId, LocalDate fecha);

    List<RegistroHabito> porParticipanteYFecha(UserId participanteId, LocalDate fecha);

    /** Para el scheduler nocturno: todos los registros en ese estado con fecha anterior a la dada (blind expire, mismo criterio que `expirePendingTracksForTrainees`). */
    List<RegistroHabito> enEstadoConFechaAnteriorA(EstadoRegistro estado, LocalDate fecha);
}
