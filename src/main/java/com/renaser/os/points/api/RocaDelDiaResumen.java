package com.renaser.os.points.api;

import java.util.UUID;

/**
 * Proyección pública de una Roca Diaria para el módulo que compone
 * {@code GET /home} (widget {@code rocksToday}) — deliberadamente sin
 * {@code RocaDiaria} completa: {@code RocaDiariaId}, {@code ColorPareto},
 * {@code EjeObjetivo}, etc. viven en {@code rocks.domain} (paquete interno)
 * y no cruzan el {@code @NamedInterface("api")}. Mismo criterio que
 * {@code habits.api.EntradaDiarioSummary}.
 */
public record RocaDelDiaResumen(UUID id, String titulo, String descripcion, boolean completada) {
}
