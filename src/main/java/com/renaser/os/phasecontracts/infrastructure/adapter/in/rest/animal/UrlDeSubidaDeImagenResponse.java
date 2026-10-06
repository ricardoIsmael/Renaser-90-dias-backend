package com.renaser.os.phasecontracts.infrastructure.adapter.in.rest.animal;

import com.renaser.os.phasecontracts.application.ports.in.animal.CambiarImagenDeAnimalUseCase.UrlDeSubida;

public record UrlDeSubidaDeImagenResponse(String url, String ruta) {

    static UrlDeSubidaDeImagenResponse from(UrlDeSubida subida) {
        return new UrlDeSubidaDeImagenResponse(subida.url().toString(), subida.ruta());
    }
}
