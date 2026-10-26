package com.renaser.os.users.application.ports.in.admin;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;

import java.util.List;
import java.util.UUID;

/**
 * Los roles que Administración puede elegir en «Equipo y roles» y al aprobar una solicitud (spec 002, fase 2, FR-031;
 * contrato §4.5; D-272): los seis roles base y los roles personalizados activos. Solo ADMIN y ALQUIMISTA activos (403).
 * Soporte TI no figura: se asigna solo desde la base (D-267).
 */
public interface ConsultarRolesAsignablesUseCase {

    List<RolAsignable> rolesAsignables(UserId actor);

    /**
     * @param clave              el {@code UserRole} de un rol base, o {@code rp:<id>} de uno personalizado
     * @param nombre             el nombre para mostrar (el de {@code UserRole.nombreVisible()} en los base)
     * @param rolBase            el rol que guarda la cuenta (el mismo, en los base)
     * @param rolPersonalizadoId {@code null} en los base
     */
    record RolAsignable(String clave, String nombre, UserRole rolBase, UUID rolPersonalizadoId) {
    }
}
