package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;

/**
 * DTO liviano expuesto entre modulos — nunca el User completo (CLAUDE.MD §5.1). Por
 * ejemplo, `habits` pregunta el rol/estado de un aprendiz sin ver su email ni su bio.
 *
 * <p>{@code rolPersonalizado} (spec 002, fase 2; D-269): el rol personalizado vigente de la cuenta (activo y que hereda
 * de {@code role}), o {@code null}. Solo para mostrar su nombre: {@code role} sigue siendo el rol base, y es el que
 * decide todo lo demás. Va al final y con un constructor sin él, para no tocar a quien ya arma un resumen.
 */
public record UserSummary(UserId id, String fullName, String avatarUrl, UserRole role, UserStatus status,
                          RolPersonalizadoAsignado rolPersonalizado) {

    /** Sin rol personalizado (la firma de siempre). */
    public UserSummary(UserId id, String fullName, String avatarUrl, UserRole role, UserStatus status) {
        this(id, fullName, avatarUrl, role, status, null);
    }
}
