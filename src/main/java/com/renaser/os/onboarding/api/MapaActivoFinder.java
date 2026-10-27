package com.renaser.os.onboarding.api;

import com.renaser.os.shared.domain.UserId;

/**
 * Si la persona ya dio por terminado su Mapa de Renacimiento (flujo {@code mapa_dia7}), o sea si el Mapa
 * esta <b>activo</b> (D-284).
 *
 * <p><b>Por que existe aparte de {@link MapaDeRenacimientoFinder}.</b> Ese trae el Mapa entero (objetivos,
 * hitos, acciones, protocolos) y {@code rocks} necesita un solo dato en cada {@code PUT} de una Roca Maestra:
 * decidir si el objetivo de 90 dias todavia se puede reemplazar o ya quedo fijo. Es la misma marca que la
 * app lee como {@code stageCompleted} en {@code GET /api/v1/mapa-renacimiento}: la que pone
 * {@code POST /api/v1/mapa-renacimiento/completar}, que la app llama <b>despues</b> de escribir las rocas.
 */
public interface MapaActivoFinder {

    /** {@code false} tambien para quien nunca abrio el Mapa. Nunca falla por eso. */
    boolean activo(UserId usuarioId);
}
