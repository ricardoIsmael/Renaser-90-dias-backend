package com.renaser.os.users.application.ports.out.autenticacion;

/**
 * Envio de correo transaccional. No existia un puerto de email en el proyecto (se busco en
 * {@code notifications}, que solo tiene {@code PushPort} para notificaciones push — nada de
 * mail). Hoy lo usa unicamente el reseteo de contrasena; si otro modulo necesita mandar mail en
 * el futuro, este puerto se generaliza en vez de duplicarse (decision a confirmar, ver docs/
 * MODULO_AUTH.md fase 7).
 */
public interface EnviarEmailPort {

    /**
     * {@code token} viaja tal cual, en claro: quien implemente este puerto arma el link
     * completo hacia el frontend. La URL base es configuracion (todavia sin definir por
     * producto), nunca hardcodeada aca.
     */
    void enviarResetContrasena(String destinatarioEmail, String token);
}
