package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.ResultadoLoginSocial;

import java.util.UUID;

/**
 * Respuesta de {@code POST /api/v1/auth/social} cuando la identidad YA tenia una solicitud
 * abierta y sigue PENDIENTE. No es un error: la persona ya se registro y espera aprobacion, y
 * la app tiene que poder mostrarle eso en vez de un fallo (A-7).
 *
 * <p>Viaja con <b>202 Accepted</b> y con cuerpo propio, no por el {@code ApiErrorResponse} del
 * handler global.
 *
 * <p><b>Corregido 2026-09-01 (D-65, docs/MODULO_AUTH.md §6.10).</b> Hasta entonces
 * {@code EstadoSolicitudSocial} tenia una segunda variante, {@code CREADA}, para cuando la
 * identidad era nueva y el alta se completaba en la misma llamada. Esa variante dejo de
 * producirse: la identidad nueva ahora devuelve {@link RegistroPendienteSocialResponse} (un
 * cuerpo distinto, sin AccountRequest todavia) y la solicitud se abre recien en
 * {@code POST /auth/social/complete}. Este record queda para el UNICO estado que sigue
 * viajando por {@code POST /auth/social}: "tu solicitud sigue en revision".
 *
 * <p>{@code accountRequestId} conserva a proposito el nombre que ya usaba
 * {@code AccountRequestIdResponse}: para un cliente que solo leia ese campo, este cuerpo sigue
 * siendo compatible.
 */
public record SolicitudSocialResponse(UUID accountRequestId, EstadoSolicitudSocial estado) {

    public enum EstadoSolicitudSocial {
        /** Ya existia y sigue PENDIENTE: volver a tocar "Continuar con Google" no crea otra. */
        EN_REVISION
    }

    public static SolicitudSocialResponse enRevision(ResultadoLoginSocial.SolicitudEnRevision resultado) {
        return new SolicitudSocialResponse(resultado.solicitudId().value(), EstadoSolicitudSocial.EN_REVISION);
    }
}
