package com.renaser.os.chat.application.ports.in.miembro;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;

/**
 * Proyeccion liviana de un usuario para un directorio/roster de chat — nunca el
 * {@code UserSummary} completo ni menos el {@code User} de `users` (CLAUDE.MD sec. 5.1:
 * solo lo que la pantalla necesita para pintar una fila).
 */
public record MiembroResumen(UserId id, String nombreCompleto, String avatarUrl, UserRole rol) {
}
