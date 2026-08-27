package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.users.application.ports.in.admin.ListStaffUseCase.PaginaStaff;
import com.renaser.os.users.infrastructure.adapter.in.rest.user.UserResponse;

import java.util.List;

/** Proyeccion de pagina para el panel admin de staff (gap #6). */
public record StaffPageResponse(List<UserResponse> content, long total, int page, int size) {

    public static StaffPageResponse from(PaginaStaff pagina) {
        List<UserResponse> content = pagina.contenido().stream().map(UserResponse::from).toList();
        return new StaffPageResponse(content, pagina.total(), pagina.page(), pagina.size());
    }
}
