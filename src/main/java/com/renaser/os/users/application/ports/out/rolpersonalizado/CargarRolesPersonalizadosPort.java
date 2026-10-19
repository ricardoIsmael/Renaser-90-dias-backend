package com.renaser.os.users.application.ports.out.rolpersonalizado;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Leer los roles personalizados y a quién están asignados (V105, D-272). */
public interface CargarRolesPersonalizadosPort {

    Optional<RolPersonalizado> porId(UUID id);

    /** Todos, activos e inactivos, por nombre. */
    List<RolPersonalizado> todos();

    /**
     * Cuántas cuentas tiene asignadas cada rol (cualquier estado). Un rol sin personas no figura. Sin la cuenta de prueba
     * de «ver como» (D-273): no es una persona, y con ella un rol que nadie usa no se podría borrar.
     */
    Map<UUID, Long> personasPorRol();

    /** Igual que {@link #personasPorRol}, para un rol: sin la cuenta de prueba de «ver como». */
    long personasCon(UUID rolId);

    /**
     * Las cuentas con un rol personalizado vigente: activo y que hereda del rol de la cuenta. <b>Incluye</b> la cuenta de
     * prueba de «ver como»: la regla de disponibilidad la tiene que restringir como a cualquiera.
     */
    Map<UserId, UUID> asignacionesVigentes();

    /** El rol personalizado vigente de una cuenta, si tiene. */
    Optional<UUID> vigenteDe(UserId usuario);
}
