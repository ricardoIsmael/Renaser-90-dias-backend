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

    /**
     * La misma busqueda que {@link #porParticipanteHabitoYFecha} pero CON bloqueo, para quien no
     * solo mira el registro sino que decide sobre su estado y despues lo escribe.
     *
     * <p>No es una comodidad. Quien decide tiene que leer BAJO el cerrojo y, ademas, esa tiene
     * que ser la PRIMERA lectura de la fila en su transaccion: una lectura previa sin cerrojo
     * deja la entidad gestionada, y entonces la consulta con cerrojo le devuelve esa instancia
     * vieja en vez de rehidratarla (el detalle de Hibernate esta en el javadoc del metodo del
     * repositorio). El resultado seria un cerrojo que se toma de verdad pero llega tarde:
     * protege la escritura, no la decision.
     */
    Optional<RegistroHabito> porParticipanteHabitoYFechaParaEscritura(UserId participanteId, HabitoId habitoId,
                                                                        LocalDate fecha);

    List<RegistroHabito> porParticipanteYFecha(UserId participanteId, LocalDate fecha);

    /** Para el scheduler nocturno: todos los registros en ese estado con fecha anterior a la dada (blind expire, mismo criterio que `expirePendingTracksForTrainees`). */
    List<RegistroHabito> enEstadoConFechaAnteriorA(EstadoRegistro estado, LocalDate fecha);
}
