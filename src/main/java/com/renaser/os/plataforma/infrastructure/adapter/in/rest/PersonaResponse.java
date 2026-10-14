package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.BuscarPersonasUseCase.PersonaEncontrada;

/** Un elemento de {@code GET /soporte-ti/personas?q=} (contrato fase 2 §3.4); {@code rol} con su nombre de la API. */
record PersonaResponse(String id, String nombre, String email, String rol) {

    static PersonaResponse from(PersonaEncontrada persona) {
        return new PersonaResponse(persona.id().toString(), persona.nombre(), persona.email(),
                persona.rol() == null ? null : persona.rol().name());
    }
}
