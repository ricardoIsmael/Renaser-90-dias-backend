package com.renaser.os.evidence.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * "De estas acciones del día (rocas diarias), con qué evidencia se cumplió cada una."
 *
 * <p>Hermano de {@link EntregasPorRegistroFinder}, que responde lo mismo para los registros de hábito. Lo pide
 * {@code rocks} para que la ficha del líder (spec 005, R-5) pueda abrir la foto de cada acción cumplida.
 *
 * <p>En lote, apoyado en {@code evidencias_roca_idx}. No recibe {@code actorId}: quien llama ya autorizó. El archivo se
 * abre aparte, por {@code GET /evidence/{id}}, con la autorización de {@code evidence}.
 */
public interface EvidenciasDeRocasFinder {

    /**
     * @return la evidencia PRINCIPAL de cada roca (la que la completó) o, si no hay principal, la más temprana. Las
     *         rocas sin ninguna evidencia no aparecen; nunca null
     */
    Map<UUID, UUID> principalesDe(Collection<UUID> rocasDiariasIds);
}
