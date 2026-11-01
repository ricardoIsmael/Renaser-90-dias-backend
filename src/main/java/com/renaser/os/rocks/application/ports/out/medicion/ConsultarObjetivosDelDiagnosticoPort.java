package com.renaser.os.rocks.application.ports.out.medicion;

import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Los objetivos de 90 días que nacieron del diagnóstico de la persona (spec 026, Q14-B). El puerto dice <b>qué necesito</b>, no de dónde sale: hoy lo
 * resuelve {@code onboarding.api.DiagnosticoDeLaPersonaFinder}.
 */
public interface ConsultarObjetivosDelDiagnosticoPort {

    /** Vacío si no tiene diagnóstico, no lo terminó o no respondió ninguna pregunta que alimente un objetivo. Nunca falla por eso. */
    List<ObjetivoDelDiagnostico> de(UserId participanteId);

    record ObjetivoDelDiagnostico(EjeObjetivo eje, String texto) {
    }
}
