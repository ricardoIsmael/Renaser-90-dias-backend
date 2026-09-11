package com.renaser.os.points.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La fórmula de cumplimiento, expuesta por su dueño.
 *
 * <p>{@code points} es el único módulo que la implementa, y este puerto es la única manera de
 * llegar a ella desde afuera: la clase pura vive en {@code points.domain} y ningún otro módulo
 * puede importarla (lo verifica {@code ArchitectureTest}). Existe para que la evaluación del
 * mentor y el ranking entre grupos usen exactamente el mismo cálculo — dos implementaciones que
 * se separan con el tiempo es el fallo que plan.md §8 quiere evitar.
 */
public interface CalculoCumplimientoPort {

    /**
     * @param obligaciones        obligaciones de evidencia ya filtradas a las exigibles. Puede
     *                            traer varias filas del mismo {@code obligacionId}: se deduplican.
     * @param ventanasPorAprendiz tramos ya intersectados (mes ∩ pertenencia ∩ asignación).
     */
    EvaluacionCumplimiento evaluar(List<ObligacionEvidencia> obligaciones,
                                    Map<UUID, List<VentanaEvaluacion>> ventanasPorAprendiz);
}
