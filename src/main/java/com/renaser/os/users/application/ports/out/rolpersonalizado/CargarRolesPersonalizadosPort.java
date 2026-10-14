package com.renaser.os.users.application.ports.out.rolpersonalizado;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Leer los roles personalizados y a quién están asignados (V105, D-269). */
public interface CargarRolesPersonalizadosPort {

    Optional<RolPersonalizado> porId(UUID id);

    /** Todos, activos e inactivos, por nombre. */
    List<RolPersonalizado> todos();

    /** Cuántas cuentas tiene asignadas cada rol (cualquier estado). Un rol sin personas no figura. */
    Map<UUID, Long> personasPorRol();

    long personasCon(UUID rolId);

    /** Las cuentas con un rol personalizado vigente: activo y que hereda del rol de la cuenta. */
    Map<UserId, UUID> asignacionesVigentes();

    /** El rol personalizado vigente de una cuenta, si tiene. */
    Optional<UUID> vigenteDe(UserId usuario);
}
