package com.renaser.os.shared.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.time.Instant;
import java.util.Map;

/**
 * La marca de una sesión de <b>solo lectura</b>: la que abre Soporte TI con «ver como rol» (spec 002, fase 2, US5;
 * contrato fase-2.md §5; D-273) sobre la cuenta de prueba de un rol.
 *
 * <p>Vive en {@code shared} porque la leen tres módulos que no se conocen: {@code plataforma} la escribe al abrir la
 * sesión, {@link FiltroDeSoloLectura} rechaza toda escritura HTTP y {@code chat} rechaza el {@code SEND} de STOMP. Los
 * valores son {@link String} y {@link Long} a propósito: el serializador de la sesión en Redis solo admite esos tipos
 * escalares además de los de Spring Security ({@code RedisSessionConfig}); un {@code UUID} o un {@code Instant} no se
 * podrían volver a leer.
 */
public final class SesionDeSoloLectura {

    /** El id (texto) de la cuenta Soporte TI que abrió la sesión. Su presencia es lo que marca la sesión. */
    public static final String ABIERTA_POR = "renaser.soloLectura.abiertaPor";
    /** Hasta cuándo vale, en milisegundos desde la época: la sesión vence aunque se siga usando (30 min). */
    public static final String VENCE_EN = "renaser.soloLectura.venceEn";
    /** El rol base elegido ({@code UserRole}, en inglés), o ausente si se eligió un rol personalizado. */
    public static final String ROL = "renaser.soloLectura.rol";
    /** El id (texto) del rol personalizado elegido, o ausente. */
    public static final String ROL_PERSONALIZADO_ID = "renaser.soloLectura.rolPersonalizadoId";
    /** El nombre del rol personalizado al abrir la sesión, para la auditoría de la salida. */
    public static final String ROL_PERSONALIZADO_NOMBRE = "renaser.soloLectura.rolPersonalizadoNombre";

    /**
     * Atributo del PEDIDO (no de la sesión) que deja {@link FiltroDeSoloLectura} cuando el pedido viene de una sesión de
     * solo lectura: lo leen los interceptores (las métricas del despliegue no cuentan estos pedidos) sin volver a abrir
     * la sesión.
     */
    public static final String EN_EL_PEDIDO = SesionDeSoloLectura.class.getName() + ".enElPedido";

    public static final String CODIGO = "MODO_SOLO_LECTURA";
    public static final String MENSAJE = "Estás viendo la app como otro rol: no se puede cambiar nada.";

    private SesionDeSoloLectura() {
    }

    public static boolean loEs(HttpSession sesion) {
        return sesion != null && sesion.getAttribute(ABIERTA_POR) instanceof String;
    }

    /** Para los atributos que copia el handshake de un WebSocket. */
    public static boolean loEs(Map<String, Object> atributosDeLaSesion) {
        return atributosDeLaSesion != null && atributosDeLaSesion.get(ABIERTA_POR) instanceof String;
    }

    /**
     * Si ya pasó la hora de vencimiento guardada en {@link #VENCE_EN}. Sin una fecha legible no se sabe hasta cuándo
     * vale: se trata como vencida (falla cerrado).
     */
    public static boolean vencida(Object venceEn, Instant ahora) {
        return !(venceEn instanceof Number milisegundos) || ahora.toEpochMilli() >= milisegundos.longValue();
    }

    /** Si el pedido pasó por {@link FiltroDeSoloLectura} con una sesión de solo lectura. */
    public static boolean vieneDeUna(HttpServletRequest pedido) {
        return pedido != null && pedido.getAttribute(EN_EL_PEDIDO) != null;
    }
}
