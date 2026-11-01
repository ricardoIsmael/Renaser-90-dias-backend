package com.renaser.os.rag.domain.model.mapa;

/** Lo mínimo del diagnóstico que va en CADA turno (spec 026): si lo tiene, si lo terminó y su prioridad. Todo lo demás lo da {@code consultar_mi_diagnostico}. */
public record ResumenDelDiagnostico(boolean completado, String prioridad) {
}
