package com.renaser.os.rag.application.ports.out.ia;

import java.util.List;
import java.util.Optional;

/**
 * Genera el análisis semanal del Espejo Sombra a partir de las entradas de diario de
 * un aprendiz. {@code Optional.empty()} representa "la IA no respondió" (mismo criterio
 * que {@code evidence.ValidacionIAPort} con {@code NO_DISPONIBLE}) — quien orqueste
 * (agregado {@code espejosombra}, de otro agente de este módulo) decide qué hacer con
 * un análisis ausente (reintentar, no generar informe esa semana, etc.).
 *
 * <p><b>SIN IA en este alcance</b>: la única implementación es
 * {@code NoOpInsightSemanalAdapter}.
 *
 * <p><b>CONTRATO COMPARTIDO — firma congelada.</b>
 */
public interface GenerarInsightSemanalPort {

    Optional<InsightSemanal> analizar(List<String> entradasDiario);

    record InsightSemanal(String patronDominante, int pctPasado, int pctPresente, int pctFuturo,
                           String insight, List<String> preguntasConfrontacion) {
    }
}
