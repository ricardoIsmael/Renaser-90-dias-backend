package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.RolPersonalizadoEnConsola;

import java.time.Instant;
import java.util.List;

/** Un elemento de {@code GET /soporte-ti/roles} (contrato de la fase 2, §4.5; D-269). */
record RolPersonalizadoEnConsolaResponse(String id, String clave, String nombre, String descripcion, String rolBase,
                                         boolean activo, List<String> funcionalidades, long personas, long version,
                                         FuncionalidadEnConsolaResponse.ActorResponse actualizadoPor,
                                         Instant actualizadoEn) {

    static RolPersonalizadoEnConsolaResponse from(RolPersonalizadoEnConsola rol) {
        return new RolPersonalizadoEnConsolaResponse(rol.id().toString(), rol.clave(), rol.nombre(), rol.descripcion(),
                rol.rolBase().name(), rol.activo(), rol.funcionalidades(), rol.personas(), rol.version(),
                FuncionalidadEnConsolaResponse.ActorResponse.from(rol.actualizadoPor()), rol.actualizadoEn());
    }
}
