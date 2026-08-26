package com.renaser.os.users.application.ports.in.autenticacion;

import com.renaser.os.shared.domain.UserId;

/**
 * Invalida TODAS las sesiones activas de un usuario, en cualquier instancia — no solo la de la
 * request actual (eso lo resuelve {@code SesionWebAdapter.cerrar}). Hoy lo usa el reseteo de
 * contrasena (docs/MODULO_AUTH.md §4.1); es el mismo mecanismo que hace falta el dia que
 * suspender a alguien deba revocarle la sesion en el acto (§7.4).
 */
public interface CerrarTodasLasSesionesUseCase {

    void cerrarTodas(UserId usuarioId);
}
