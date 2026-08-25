package com.renaser.os.evidence.application.ports.out.ia;

import com.renaser.os.evidence.domain.model.evidencia.Evidencia;

/**
 * Puerto de validación IA — el límite de reintentos (CLAUDE.MD §7: "es lógica de
 * dominio, no de infraestructura") vive en {@link Evidencia#registrarIntentoFallido()},
 * no acá. Esta interfaz solo pregunta "¿qué dijo la IA?".
 *
 * <p><b>SIN IA en este alcance</b>: la única implementación es
 * {@code NoOpValidacionIAAdapter}, que siempre devuelve {@code NO_DISPONIBLE} — la
 * integración real con Gemini/Vertex vía Spring AI {@code ChatClient} es una fase
 * futura (Ola 5, ver {@code docs/MODULO_EVIDENCE.md}).
 */
public interface ValidacionIAPort {

    ResultadoValidacionIA validar(Evidencia evidencia);
}
