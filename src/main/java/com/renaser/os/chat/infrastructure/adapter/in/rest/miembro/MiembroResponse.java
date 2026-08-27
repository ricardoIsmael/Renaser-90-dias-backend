package com.renaser.os.chat.infrastructure.adapter.in.rest.miembro;

import com.renaser.os.chat.application.ports.in.miembro.MiembroResumen;

/** {@code role} ya viaja en ingles en {@code UserRole} (TRAINEE/MENTOR/MENTOR_LEAD/
 * ADMIN/ALCHEMIST) — a diferencia de los enums propios de `chat`, este no necesita
 * traduccion D-36 porque `users.api` ya lo expone asi. */
public record MiembroResponse(String id, String fullName, String avatarUrl, String role) {

    public static MiembroResponse from(MiembroResumen m) {
        return new MiembroResponse(m.id().toString(), m.nombreCompleto(), m.avatarUrl(), m.rol().name());
    }
}
