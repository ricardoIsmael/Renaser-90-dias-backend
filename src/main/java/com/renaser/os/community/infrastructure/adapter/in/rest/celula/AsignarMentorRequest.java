package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** {@code leaderUserId} — el nombre que ya usan los clientes nuevos
 * (community/schema.ts:65-68); {@code mentorProfileId} legado ya no aplica porque
 * `perfiles_mentor.usuario_id` ES el `usuario_id` en el modelo nuevo (P-16 del baseline),
 * asi que ambos campos, de existir, serian el mismo valor. */
public record AsignarMentorRequest(@NotNull UUID leaderUserId) {
}
