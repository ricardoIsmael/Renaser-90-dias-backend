package com.renaser.os.users.api;

/**
 * Estado de la cuenta. Publico por la misma razon que UserRole: `UserSummary` lo expone
 * y todo modulo debe poder rechazar a un usuario suspendido.
 */
public enum UserStatus {

    ACTIVE,

    /**
     * Registrado pero todavia sin aprobar (2026-08-27, resuelve la pregunta abierta R-3, que
     * quedo cuando {@code estado_usuario.INACTIVO} de Postgres no tenia significado en el
     * dominio).
     *
     * <p>Existe porque el alta pasa a capturar la contrasena EN EL FORMULARIO, no despues de la
     * aprobacion: la fila de {@code usuarios} se crea al registrarse (es el unico lugar donde
     * vive {@code hash_contrasena}) y la aprobacion solo la mueve a {@link #ACTIVE}. Sin este
     * estado habria que elegir entre duplicar la credencial en {@code solicitudes_cuenta} o
     * dejar entrar a alguien sin aprobar.
     *
     * <p>No da acceso: {@link #allowsAccess()} sigue siendo {@code == ACTIVE}, asi que el login
     * lo rechaza por construccion (via {@code CredencialParaLogin.cuentaHabilitada()}) sin que
     * haga falta un {@code if} nuevo en ningun lado.
     */
    INACTIVE,

    SUSPENDED;

    public boolean allowsAccess() {
        return this == ACTIVE;
    }
}
