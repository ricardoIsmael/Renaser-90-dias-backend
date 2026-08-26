package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.ia.GenerarInsightSemanalPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Placeholder mientras no hay credenciales de Gemini (D-39). {@code Optional.empty()}
 * es el mismo criterio que {@code evidence.ValidacionIAPort} con {@code NO_DISPONIBLE}:
 * quien orqueste el Espejo Sombra decide qué hacer con un análisis ausente.
 *
 * <p>CLAUDE.MD sec. 5.4.9: nunca se loguea el contenido de las entradas de diario que
 * alimentan este análisis (dato sensible) — solo el hecho de que la IA no respondió.
 */
@Component
public class NoOpInsightSemanalAdapter implements GenerarInsightSemanalPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpInsightSemanalAdapter.class);

    @Override
    public Optional<InsightSemanal> analizar(List<String> entradasDiario) {
        log.warn("GenerarInsightSemanalPort.analizar(...) placeholder: faltan credenciales de IA (D-39).");
        return Optional.empty();
    }
}
