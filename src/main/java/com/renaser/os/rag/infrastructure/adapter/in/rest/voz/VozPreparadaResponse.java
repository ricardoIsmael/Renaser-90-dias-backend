package com.renaser.os.rag.infrastructure.adapter.in.rest.voz;

/**
 * Respuesta de {@code POST /api/v1/renasia/voz} (D-159): de donde bajar el audio mientras se
 * genera. Es una ruta, no una URL completa: la app le antepone su propio host.
 */
public record VozPreparadaResponse(String audio) {
}
