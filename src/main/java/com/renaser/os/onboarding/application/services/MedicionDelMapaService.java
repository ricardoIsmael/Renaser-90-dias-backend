package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.MedicionDelMapaFinder;
import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort.ValorDeRespuesta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * Implementa {@link MedicionDelMapaFinder}: seis respuestas del Mapa, en una sola consulta.
 *
 * <p>No valida los valores contra ningun catalogo. Si alguien contesto algo que el catalogo de la
 * V41 no tiene, sale tal cual y quien consume decide: {@code rocks} trata lo desconocido como
 * "todavia no eligio", que es lo honesto. Filtrar aca obligaria a duplicar en Java una lista que ya
 * vive en {@code opciones_pregunta_onboarding}.
 */
@Service
public class MedicionDelMapaService implements MedicionDelMapaFinder {

    private static final String SALUD_TIPO = "map_health_result_type";
    private static final String SALUD_UNIDAD = "map_health_unit";
    private static final String NEGOCIO_TIPO = "map_business_result_type";
    private static final String NEGOCIO_PERIODO = "map_business_period";
    private static final String RELACIONES_BASE = "map_relations_baseline_scale";
    private static final String RELACIONES_META = "map_relations_target_scale";

    private static final Set<String> CLAVES = Set.of(SALUD_TIPO, SALUD_UNIDAD, NEGOCIO_TIPO, NEGOCIO_PERIODO,
            RELACIONES_BASE, RELACIONES_META);

    private final LeerRespuestasPorClavePort leerRespuestasPort;

    public MedicionDelMapaService(LeerRespuestasPorClavePort leerRespuestasPort) {
        this.leerRespuestasPort = leerRespuestasPort;
    }

    @Override
    @Transactional(readOnly = true)
    public MedicionDelMapa delParticipante(UserId usuarioId) {
        Map<String, ValorDeRespuesta> respuestas = leerRespuestasPort.deUsuario(usuarioId, CLAVES);
        return new MedicionDelMapa(
                texto(respuestas, SALUD_TIPO),
                texto(respuestas, SALUD_UNIDAD),
                texto(respuestas, NEGOCIO_TIPO),
                texto(respuestas, NEGOCIO_PERIODO),
                escala(respuestas, RELACIONES_BASE),
                escala(respuestas, RELACIONES_META));
    }

    private static String texto(Map<String, ValorDeRespuesta> respuestas, String clave) {
        ValorDeRespuesta valor = respuestas.get(clave);
        return valor == null || valor.texto() == null || valor.texto().isBlank() ? null : valor.texto().trim();
    }

    private static Integer escala(Map<String, ValorDeRespuesta> respuestas, String clave) {
        ValorDeRespuesta valor = respuestas.get(clave);
        return valor == null || valor.escala() == null ? null : valor.escala().intValue();
    }
}
