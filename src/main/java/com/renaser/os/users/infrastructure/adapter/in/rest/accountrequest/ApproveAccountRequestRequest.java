package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import com.renaser.os.users.api.UserRole;

import java.util.UUID;

/**
 * Cuerpo OPCIONAL de {@code POST /api/v1/account-requests/{id}/approve} (D-265). {@code role} es el rol con el que
 * entra (los mismos códigos que {@code PATCH /users/{id}/role}); sin cuerpo o sin {@code role}, entra como TRAINEE,
 * que es lo que mandan la app 1.5.0 y el panel de solicitudes.
 *
 * <p>{@code rolPersonalizadoId} (spec 002, fase 2; D-269), opcional y aditivo: entra con ese rol personalizado; el rol
 * base sale de él y {@code role} se ignora.
 */
public record ApproveAccountRequestRequest(UserRole role, UUID rolPersonalizadoId) {
}
