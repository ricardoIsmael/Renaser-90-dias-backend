package com.renaser.os.calendar.application.ports.in.meet;

import com.renaser.os.calendar.domain.model.meet.AccionDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.EstadoDeLaCuenta;

import java.time.Instant;
import java.util.List;

/**
 * La pantalla «Cuenta de Google» (spec 040, Story 4): si hay cuenta conectada y de quién, si sirve, cuándo se usó por última vez, el último error
 * y las últimas acciones. Nunca lleva el permiso permanente.
 *
 * @param configurada el servidor tiene las credenciales de Google y la clave de cifrado; sin eso no se puede conectar
 * @param estado      {@code null} si no hay cuenta
 */
public record EstadoDeLaCuentaVista(boolean configurada, boolean conectada, String correo, EstadoDeLaCuenta estado,
                                    Instant conectadaEn, Instant ultimoUsoEn, String ultimoError,
                                    List<AccionVista> acciones) {

    /** Una fila de la auditoría. {@code quien} es el nombre de la persona o «el sistema». */
    public record AccionVista(Instant en, AccionDeAuditoriaGoogle accion, String quien, boolean exito, Integer codigoHttp) {
    }
}
