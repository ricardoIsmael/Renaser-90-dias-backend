package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

/** Campos null = "no cambiar". Sin `role` a proposito: eso sigue siendo `PATCH /users/{id}/role`. */
public record UpdateStaffProfileRequest(String fullName, String avatarUrl, String bio, String department) {
}
