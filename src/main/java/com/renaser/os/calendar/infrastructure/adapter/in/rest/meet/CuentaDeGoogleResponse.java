package com.renaser.os.calendar.infrastructure.adapter.in.rest.meet;

import com.renaser.os.calendar.application.ports.in.meet.EstadoDeLaCuentaVista;

import java.util.List;

/** La pantalla «Cuenta de Google»: nunca lleva el permiso permanente ni ningún token (spec 040, FR-011). */
record CuentaDeGoogleResponse(boolean configurada, boolean conectada, String correo, String estado, String conectadaEn,
                              String ultimoUso, String ultimoError, List<AccionResponse> acciones) {

    record AccionResponse(String en, String accion, String quien, boolean exito, Integer codigoHttp) {
    }

    static CuentaDeGoogleResponse from(EstadoDeLaCuentaVista v) {
        return new CuentaDeGoogleResponse(v.configurada(), v.conectada(), v.correo(), v.estado() == null ? null : v.estado().name(),
                v.conectadaEn() == null ? null : v.conectadaEn().toString(), v.ultimoUsoEn() == null ? null : v.ultimoUsoEn().toString(),
                v.ultimoError(), v.acciones().stream().map(a -> new AccionResponse(a.en().toString(), a.accion().name(), a.quien(),
                        a.exito(), a.codigoHttp())).toList());
    }
}
