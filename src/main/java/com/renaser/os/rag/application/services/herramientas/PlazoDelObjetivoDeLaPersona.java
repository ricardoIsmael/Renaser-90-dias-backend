package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.shared.domain.DuracionDelPrograma;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cómo se llama el plazo del objetivo grande de ESTA persona en lo que SER le lee y le dice: «90 días», «3 meses» o «1 año» (decisión del dueño, 2026-10-31).
 * Sale de su situación ({@code duracionDias} y formación), la misma que arma el prompt. Best-effort: si no hay situación (no hace el programa) o la
 * consulta falla, es el de siempre, «90 días»: un texto de más no vale una herramienta caída.
 */
final class PlazoDelObjetivoDeLaPersona {

    private static final Logger log = LoggerFactory.getLogger(PlazoDelObjetivoDeLaPersona.class);

    private PlazoDelObjetivoDeLaPersona() {
    }

    static String de(ConsultarSituacionDelAprendizPort situacion, UserId persona) {
        try {
            return situacion.de(persona).map(SituacionDelAprendiz::plazoDelObjetivo)
                    .orElse(DuracionDelPrograma.plazoDelObjetivo(DuracionDelPrograma.POR_DEFECTO));
        } catch (RuntimeException falla) {
            log.info("[rag] el plazo del objetivo sale con el de siempre: {}", falla.toString());
            return DuracionDelPrograma.plazoDelObjetivo(DuracionDelPrograma.POR_DEFECTO);
        }
    }
}
