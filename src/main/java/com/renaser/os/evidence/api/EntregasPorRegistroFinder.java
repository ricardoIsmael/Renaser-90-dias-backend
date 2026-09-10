package com.renaser.os.evidence.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * "De estos registros, cuándo entregaron y en qué quedó la revisión."
 *
 * <p>Hermano de {@link RegistrosConEvidenciaFinder}, que responde solo sí/no. Ese alcanza para
 * que la pantalla del aprendiz no le vuelva a pedir un archivo que ya subió; no alcanza para el
 * seguimiento del mentor ni para la evaluación mensual, que necesitan <b>cuándo</b> —para saber
 * si la entrega cae dentro del tramo atribuible— y el estado de revisión por separado.
 *
 * <p>En lote, apoyado en {@code evidencias_registro_idx}. No recibe {@code actorId}: quien llama
 * ya autorizó, igual que en el finder hermano.
 */
public interface EntregasPorRegistroFinder {

    /**
     * @return una entrada por registro que tenga al menos una evidencia. Los registros sin
     *         ninguna simplemente no aparecen — su ausencia significa "no entregó", que es
     *         distinto de "no lo sé"; esa diferencia la resuelve quien conoce las obligaciones.
     */
    Map<UUID, EntregaDeEvidencia> porRegistros(Collection<UUID> registrosHabitoIds);
}
