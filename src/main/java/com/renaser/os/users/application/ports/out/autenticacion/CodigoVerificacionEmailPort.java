package com.renaser.os.users.application.ports.out.autenticacion;

import java.time.Duration;

/**
 * Codigo de un solo uso para probar que el solicitante controla una casilla de correo, ANTES
 * de que exista ninguna cuenta (2026-08-27, docs/PLAN_INTEGRACION_FRONTEND.md — reemplaza el
 * {@code signInWithOtp}/{@code verifyOtp} de Supabase que el alta ya no puede usar). Vive solo
 * en Redis, mismo criterio de {@link TokenResetContrasenaPort}: la BD esta congelada (D-40) y
 * un codigo efimero no tiene sentido como tabla.
 *
 * <p>Keyed por EMAIL, no por {@code UserId}: a esta altura del flujo todavia no existe ningun
 * usuario — es literalmente lo que este codigo esta tratando de habilitar.
 *
 * <p><b>Limite de intentos, no solo TTL</b> (OWASP Multifactor Authentication Cheat Sheet:
 * "apply strict attempt limits"; un codigo de 6 digitos tiene ~1 millon de combinaciones, muy
 * poco contra fuerza bruta sin limitar intentos): {@link #verificarCodigo} cuenta los intentos
 * fallidos y invalida el codigo entero al llegar al maximo, no solo lo compara.
 */
public interface CodigoVerificacionEmailPort {

    /**
     * Genera un codigo numerico nuevo para {@code email} y lo guarda con el vencimiento dado,
     * reemplazando cualquier codigo anterior para ese mismo email (y su contador de intentos):
     * pedir un codigo nuevo invalida el viejo, para que nunca haya dos codigos "validos" a la
     * vez para la misma casilla — evita la confusion de cual de los dos correos hay que mirar.
     */
    String generarCodigo(String email, Duration vigencia);

    /**
     * Compara {@code codigo} contra el guardado para {@code email}. Si coincide, lo consume
     * (borra) y devuelve {@code true} — un solo uso, igual que un token de reset. Si NO
     * coincide, cuenta el intento fallido y devuelve {@code false}; al llegar a
     * {@code maxIntentos} fallidos, borra el codigo completo (no solo deja de aceptarlo) para
     * forzar pedir uno nuevo en vez de dejarlo "vivo" indefinidamente para seguir probando
     * hasta que venza el TTL.
     */
    boolean verificarCodigo(String email, String codigo, int maxIntentos);
}
