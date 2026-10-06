package com.renaser.os.habits.application.ports.out.registro;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    /**
     * El {@code dia_programa} MAS ALTO con el que cada uno de estos habitos ya genero un registro
     * para este participante (D-196). Es la prueba de que el habito ya estuvo activo: el snapshot
     * del registro dice en que dia del programa se genero, asi que un maximo >= al
     * {@code dia_desbloqueo} significa que la persona llego a ese dia y el habito corrio.
     *
     * <p>Una sola consulta agregada; los habitos sin ningun registro no aparecen en el mapa.
     */
    Map<HabitoId, Integer> diaProgramaMasAltoGeneradoPorHabito(UserId participanteId, Collection<HabitoId> habitos);

    /**
     * Para el barrido de expiracion (E-534): lo {@code PENDIENTE} de UN participante con fecha anterior a su hoy.
     * Reemplaza a {@code enEstadoConFechaAnteriorA(estado, fecha)}, que traia lo de TODO el padron con una sola fecha
     * (la UTC) y es justamente el error: el dia de cada persona termina a su medianoche.
     */
    List<RegistroHabito> pendientesDeParticipanteAnterioresA(UserId participanteId, LocalDate fecha);
}
