package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.FuncionalidadEnConsola;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Un elemento de {@code GET /soporte-ti/funcionalidades} (contrato §6). */
record FuncionalidadEnConsolaResponse(String clave, String madre, String area, String nombre, String descripcion,
                                      List<String> rolesQueLaUsan, boolean critica, String porDefecto,
                                      String procesos, boolean prendidaGlobal, Map<String, Boolean> porRol,
                                      String aviso, long version, ActorResponse actualizadoPor,
                                      Instant actualizadoEn, CircuitoResponse circuito) {

    static FuncionalidadEnConsolaResponse from(FuncionalidadEnConsola f) {
        Map<String, Boolean> porRol = new LinkedHashMap<>();
        f.porRol().forEach((rol, prendida) -> porRol.put(rol.name(), prendida));
        return new FuncionalidadEnConsolaResponse(f.clave(), f.madre(), f.area(), f.nombre(), f.descripcion(),
                f.rolesQueLaUsan().stream().map(Enum::name).sorted().toList(), f.critica(), f.porDefecto().name(),
                f.procesos().name(), f.prendidaGlobal(), porRol, f.aviso(), f.version(),
                ActorResponse.from(f.actualizadoPor()), f.actualizadoEn(),
                f.circuito() == null ? null : CircuitoResponse.from(f.circuito()));
    }

    record ActorResponse(String id, String nombre) {

        static ActorResponse from(FuncionalidadEnConsola.Actor actor) {
            return actor == null ? null : new ActorResponse(actor.id().toString(), actor.nombre());
        }
    }
}
