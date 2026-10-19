package com.renaser.os.users.application.ports.in.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;

import java.util.UUID;

/**
 * Solo el camino simple (CLAUDE.MD §4.3): si el nuevo rol es MENTOR y no existe
 * MentorProfile todavia, se crea uno vacio. Migracion de datos entre perfiles al
 * cambiar de rol queda fuera de alcance, explicitamente.
 *
 * <p><b>Rol personalizado</b> (spec 002, fase 2, FR-031; D-272): con {@code rolPersonalizadoId}, la cuenta toma el rol
 * base de ese rol y se ignora {@code newRole}; sin él, se cambia el rol base y se quita el rol personalizado que
 * tuviera. Un rol que hereda de ADMIN o ALQUIMISTA lo asigna solo un ADMIN (403); uno desactivado o inexistente es 400.
 */
public interface UpdateUserRoleUseCase {

    void updateRole(UpdateUserRoleCommand command);

    /**
     * @param newRole            el rol base; puede faltar si viene {@code rolPersonalizadoId}
     * @param rolPersonalizadoId opcional (D-272)
     */
    record UpdateUserRoleCommand(UserId targetUserId, UserRole newRole, UserId actorId, UUID rolPersonalizadoId) {

        /**
         * Validación a mano y no con {@code SelfValidating}: con dos constructores, ese ayudante valida el primero que
         * devuelve la reflexión, que no tiene por qué ser el canónico.
         */
        public UpdateUserRoleCommand {
            if (targetUserId == null || actorId == null) {
                throw new IllegalArgumentException("Faltan la cuenta o quien cambia el rol");
            }
            if (newRole == null && rolPersonalizadoId == null) {
                throw new IllegalArgumentException("Falta el rol nuevo");
            }
        }

        /** Sin rol personalizado: la firma de siempre. */
        public UpdateUserRoleCommand(UserId targetUserId, UserRole newRole, UserId actorId) {
            this(targetUserId, newRole, actorId, null);
        }
    }
}
