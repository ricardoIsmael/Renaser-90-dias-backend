package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.VerComoUseCase.VerComoAbierto;
import com.renaser.os.users.api.RolPersonalizadoAsignado;

import java.time.Instant;

/**
 * La respuesta de {@code POST /soporte-ti/ver-como} (contrato fase-2.md §5): {@code { cuenta: { id, nombre, rol,
 * rolPersonalizado: { id, nombre } | null }, venceEn }}. El token de la sesión nueva va en la cabecera
 * {@code X-Auth-Token}, no en el cuerpo, igual que en el login.
 */
record VerComoResponse(Cuenta cuenta, Instant venceEn) {

    static VerComoResponse from(VerComoAbierto abierto) {
        RolPersonalizadoAsignado personalizado = abierto.cuenta().rolPersonalizado();
        return new VerComoResponse(new Cuenta(abierto.cuenta().id().value().toString(), abierto.cuenta().nombre(),
                abierto.cuenta().rol().name(), personalizado == null ? null
                        : new RolPersonalizado(personalizado.id().toString(), personalizado.nombre())),
                abierto.venceEn());
    }

    record Cuenta(String id, String nombre, String rol, RolPersonalizado rolPersonalizado) {
    }

    record RolPersonalizado(String id, String nombre) {
    }
}
