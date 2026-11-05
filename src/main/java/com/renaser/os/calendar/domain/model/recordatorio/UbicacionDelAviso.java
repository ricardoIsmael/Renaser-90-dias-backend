package com.renaser.os.calendar.domain.model.recordatorio;

import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;

/**
 * Que dice un aviso de MENSAJE sobre DONDE es el evento (spec 041, FR-001 y FR-017): el enlace para entrar si el evento es en linea
 * (Meet, Zoom u otro enlace web) o la direccion si es presencial. Una llamada interna o un webinar sin enlace no dicen nada: el mensaje
 * manda a mirarlo en Renaser. Se lee del evento al DESPACHAR, no al programar: si el enlace se agrega o cambia despues, el aviso lo lleva.
 */
public final class UbicacionDelAviso {

    private UbicacionDelAviso() {
    }

    /** El enlace web para entrar, o {@code null}. */
    public static String enlace(TipoUbicacion tipo, String valor) {
        boolean enLinea = tipo == TipoUbicacion.MEET || tipo == TipoUbicacion.ZOOM || tipo == TipoUbicacion.ENLACE;
        return enLinea ? sinVacio(valor) : null;
    }

    /** La direccion del lugar, o {@code null}. */
    public static String lugar(TipoUbicacion tipo, String valor) {
        return tipo == TipoUbicacion.DIRECCION ? sinVacio(valor) : null;
    }

    private static String sinVacio(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
