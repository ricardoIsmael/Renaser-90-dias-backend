package com.renaser.os.chat.infrastructure.adapter.in.rest.bienvenida;

import com.renaser.os.chat.application.ports.in.bienvenida.CambiarPortadaDeBienvenidaUseCase.UrlDeSubida;

/**
 * @param url  a donde el teléfono hace el PUT; con el almacenamiento de marcador, {@code about:blank#…}
 * @param ruta la que se confirma después
 */
record UrlDeSubidaResponse(String url, String ruta) {

    static UrlDeSubidaResponse from(UrlDeSubida subida) {
        return new UrlDeSubidaResponse(subida.url().toString(), subida.ruta());
    }
}
