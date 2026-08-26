package com.renaser.os.users.application.ports.out.autenticacion;

import java.time.Duration;

/**
 * Limite de tasa para pedidos de reseteo de contrasena, por email y por IP (protege contra
 * enumeracion de cuentas y spam de correos). Vive en Redis, mismo criterio que
 * {@code ControlCuotaRenasiaPort} (rag): un contador de ventana no tiene sentido como tabla en
 * una BD congelada.
 */
public interface LimitarSolicitudesResetPort {

    /**
     * Registra un intento bajo {@code clave} dentro de la ventana dada.
     *
     * @return {@code true} si quedaba margen y el intento se registro; {@code false} si ya se
     * alcanzo el maximo permitido en la ventana actual.
     */
    boolean registrarIntento(String clave, Duration ventana, int maximo);
}
