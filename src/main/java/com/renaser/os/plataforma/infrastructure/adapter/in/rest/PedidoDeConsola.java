package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.AccesoDeConsola;
import com.renaser.os.shared.domain.UserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;

/**
 * Lo que la consola necesita de un pedido HTTP: el token de {@code X-Consola-Token}, el id de la sesión (los tokens de
 * la consola valen solo en la sesión que los pidió) y el dispositivo para la auditoría.
 */
final class PedidoDeConsola {

    static final String TOKEN_DE_CONSOLA = "X-Consola-Token";
    static final String REAUTENTICACION = "X-Reautenticacion";

    private PedidoDeConsola() {
    }

    static AccesoDeConsola acceso(UserId actor, HttpServletRequest request) {
        return new AccesoDeConsola(actor, request.getHeader(TOKEN_DE_CONSOLA), sesion(request));
    }

    static String dispositivo(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }

    private static String sesion(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        return sesion == null ? null : sesion.getId();
    }
}
