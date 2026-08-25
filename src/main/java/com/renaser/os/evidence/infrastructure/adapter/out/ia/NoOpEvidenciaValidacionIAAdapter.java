package com.renaser.os.evidence.infrastructure.adapter.out.ia;

import com.renaser.os.evidence.application.ports.out.ia.ResultadoValidacionIA;
import com.renaser.os.evidence.application.ports.out.ia.ValidacionIAPort;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder mientras no hay credenciales de Gemini/Vertex (D-39: {@code spring.ai.*}
 * sigue excluido en {@code application.yaml}). Siempre devuelve {@code NO_DISPONIBLE} —
 * mismo patrón EXACTO que {@code shared.infrastructure.storage.NoOpAlmacenamientoAdapter}
 * para S3 (D-34): un adapter placeholder explícito, no un puerto sin implementación.
 *
 * <p>Efecto intencional: cada corrida del scheduler de la cola incrementa
 * {@code intentosIa} de las evidencias pendientes hasta que, al tercer intento, caen a
 * {@code REVISION_MANUAL} — el fallback a revisión humana queda completo y probado sin
 * necesidad de IA real. Ver {@code docs/MODULO_EVIDENCE.md}.
 */
@Component
public class NoOpEvidenciaValidacionIAAdapter implements ValidacionIAPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpEvidenciaValidacionIAAdapter.class);

    @Override
    public ResultadoValidacionIA validar(Evidencia evidencia) {
        log.warn("ValidacionIAPort.validar({}) placeholder: faltan credenciales de IA (D-39, Ola futura).",
                evidencia.id());
        return ResultadoValidacionIA.NO_DISPONIBLE;
    }
}
