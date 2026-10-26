package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import com.renaser.os.users.api.UserRole;

import java.util.UUID;

/**
 * {@code PATCH /users/{id}/role}. {@code rolPersonalizadoId} es opcional y aditivo (spec 002, fase 2; D-272): con él, el
 * rol base sale del rol personalizado y {@code newRole} se ignora (puede faltar); sin él, {@code newRole} es obligatorio
 * —lo que manda la app 1.5.0— y se quita el rol personalizado que tuviera.
 */
public record UpdateUserRoleRequest(UserRole newRole, UUID rolPersonalizadoId) {
}
