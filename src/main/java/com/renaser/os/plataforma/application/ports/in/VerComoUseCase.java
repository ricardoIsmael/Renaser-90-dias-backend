package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.users.api.CuentaDePrueba;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * «Ver como rol» (spec 002, fase 2, US5; contrato fase-2.md §5; D-273): Soporte TI abre una sesión NUEVA de solo
 * lectura de la cuenta de prueba de un rol, sin tocar la suya, y la cierra al salir. Entrar y salir quedan auditados
 * ({@code VER_COMO}).
 */
public interface VerComoUseCase {

    /**
     * Soporte TI con la consola abierta (403 / 423), el pedido válido (400), el rol personalizado existe y está activo
     * (404 / 400); después la cuenta de prueba (y su grupo, su programa y su onboarding si el rol los tiene), la sesión y
     * la auditoría.
     */
    VerComoAbierto abrir(AbrirVerComoCommand command);

    /**
     * Cierra la sesión «ver como» con la que se hace el pedido (la de la cuenta de prueba) y lo audita a nombre de quien
     * la abrió.
     *
     * @throws java.util.NoSuchElementException si la sesión del pedido no es de «ver como» (404)
     */
    void cerrar(CerrarVerComoCommand command);

    /**
     * @param rol                el nombre del rol en la API, o {@code null}
     * @param rolPersonalizadoId el rol personalizado, o {@code null}
     */
    record AbrirVerComoCommand(AccesoDeConsola acceso, String rol, UUID rolPersonalizadoId, String dispositivo) {

        public AbrirVerComoCommand {
            Objects.requireNonNull(acceso, "acceso es obligatorio");
        }
    }

    /** @param token el id de la sesión nueva, para la cabecera {@code X-Auth-Token} de la respuesta */
    record VerComoAbierto(String token, CuentaDePrueba cuenta, Instant venceEn) {
    }

    /** @param sesion el id de la sesión del pedido ({@code null} si no tiene) */
    record CerrarVerComoCommand(String sesion, String dispositivo) {
    }
}
