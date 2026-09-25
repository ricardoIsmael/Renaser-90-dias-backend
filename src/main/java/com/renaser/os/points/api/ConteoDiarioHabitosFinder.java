package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Conteo diario de hábitos EN LOTE para el semáforo del aprendiz (D-168). Lo implementa
 * {@code habits} reutilizando la misma consulta agrupada que ya alimenta el ranking
 * ({@code ContarRegistrosDiariosHabitsPort}, D-43): una sola consulta para todos los
 * participantes pedidos, nunca una por persona.
 *
 * <p>Vive en {@code points.api} y no en {@code habits.api} por el mismo motivo que
 * {@link PorcentajeHabitosFinder}: {@code habits} ya depende de {@code points} para otorgar
 * puntos, así que la dependencia se invierte (el consumidor declara, el proveedor implementa).
 *
 * <p>Semántica de cada {@link ConteoDelDia}: {@code programados} = registros del día menos los
 * opcionales no cumplidos (un opcional cumplido sí cuenta); {@code cumplidos} = registros en
 * estado COMPLETADO. Un día sin registros no aparece.
 */
public interface ConteoDiarioHabitosFinder {

    /**
     * @param participantes una colección vacía devuelve un mapa vacío sin consultar la base
     * @param desde         primer día (fecha local del participante, inclusive)
     * @param hasta         último día (inclusive)
     * @return por participante, sus días con al menos un registro; sin clave = ningún registro
     */
    Map<UserId, List<ConteoDelDia>> porParticipanteEntre(Collection<UserId> participantes, LocalDate desde,
                                                         LocalDate hasta);
}
