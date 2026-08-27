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

    /**
     * Panel admin de staff (gap #6): comunica la contrasena temporal generada al invitar
     * a un mentor/admin/alquimista nuevo. {@code temporaryPassword} viaja EN CLARO por
     * este puerto (es la unica vez que existe en texto plano — nunca se persiste, solo se
     * hashea antes de guardar) hacia quien implemente el envio real. Mismo criterio de
     * "nunca se loguea" que {@code enviarResetContrasena} (CLAUDE.MD §5.4.9).
     */
    void enviarInvitacionStaff(String destinatarioEmail, String temporaryPassword);
}
