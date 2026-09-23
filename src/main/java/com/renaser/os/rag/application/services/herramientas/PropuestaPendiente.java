package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El ultimo paso comun de las herramientas de horario (fase 4, 2026-09-23): dejar la propuesta
 * guardada y decirle al modelo, sin ambiguedad, que TODAVIA no cambio nada. Mismo texto y misma
 * traduccion de errores que {@link PropuestaDeMarcarHabito}, para que el modelo aprenda un solo
 * idioma de propuestas.
 */
final class PropuestaPendiente {

    private static final Logger log = LoggerFactory.getLogger(PropuestaPendiente.class);

    private PropuestaPendiente() {
    }

    /**
     * @param invocacion la normalizada —ids y horas ya validados, en su formato canonico—, no la
     *                   que mando el modelo: al confirmar se ejecuta exactamente esto
     */
    static ResultadoHerramienta registrar(ProponerAccionUseCase proponerAccion, UserId actorId,
                                          InvocacionHerramienta invocacion, String resumen) {
        try {
            proponerAccion.proponer(actorId, invocacion, resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}", invocacion.nombre(), falla);
            return ResultadoHerramienta.fallo("No pude preparar la confirmacion en este momento.");
        }
        return ResultadoHerramienta.exito("Propuesta creada: " + resumen + " TODAVIA NO esta hecho: la persona "
                + "tiene que tocar Confirmar en la app para que se aplique. No digas que ya quedo cambiado; dile "
                + "que confirme con el boton.");
    }
}
