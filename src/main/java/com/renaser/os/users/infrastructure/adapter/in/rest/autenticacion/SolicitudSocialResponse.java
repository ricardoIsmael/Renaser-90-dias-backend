package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.ResultadoLoginSocial;

import java.util.UUID;

/**
 * Respuesta de {@code POST /api/v1/auth/social} cuando el login NO abre sesion porque la
 * solicitud de alta todavia no fue aprobada. Cubre los dos estados en que eso pasa, y la app
 * necesita poder distinguirlos: "acabamos de recibir tu solicitud" no se le muestra igual a
 * alguien que ya la mando la semana pasada y sigue esperando (A-7).
 *
 * <p>Ninguno de los dos es un error: son estados normales del flujo, por eso viajan con
 * <b>202 Accepted</b> y con cuerpo propio, no por el {@code ApiErrorResponse} del handler
 * global.
 *
 * <p>{@code accountRequestId} conserva a proposito el nombre que ya usaba
 * {@code AccountRequestIdResponse}: para un cliente que solo leia ese campo, este cuerpo sigue
 * siendo compatible — {@code estado} es informacion agregada, no un cambio.
 */
public record SolicitudSocialResponse(UUID accountRequestId, EstadoSolicitudSocial estado) {

    public enum EstadoSolicitudSocial {
        /** Se abrio ahora, en esta misma llamada. */
        CREADA,
        /** Ya existia y sigue PENDIENTE: volver a tocar "Continuar con Google" no crea otra. */
        EN_REVISION
    }

    public static SolicitudSocialResponse creada(ResultadoLoginSocial.SolicitudCreada resultado) {
        return new SolicitudSocialResponse(resultado.solicitudId().value(), EstadoSolicitudSocial.CREADA);
    }

    public static SolicitudSocialResponse enRevision(ResultadoLoginSocial.SolicitudEnRevision resultado) {
        return new SolicitudSocialResponse(resultado.solicitudId().value(), EstadoSolicitudSocial.EN_REVISION);
    }
}
