package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectura de los roles personalizados para otros módulos (contrato de la fase 2, §4.1; D-272). La usa {@code plataforma}
 * para la consola y para su copia en memoria: {@link #todos()} y {@link #asignaciones()} se leen enteros cada 30 s y
 * después de cada cambio, nunca por pedido.
 */
public interface RolesPersonalizadosFinder {

    /** El rol personalizado de la cuenta, si tiene uno, está activo y hereda de su rol. */
    Optional<RolPersonalizadoResumen> delUsuario(UserId usuario);

    /** Todos, activos e inactivos, ordenados por nombre. */
    List<RolPersonalizadoResumen> todos();

    /** Uno, activo o no. Agregado al contrato (§4.1) para que la consola no recorra todos para validar uno. */
    Optional<RolPersonalizadoResumen> porId(UUID id);

    /**
     * Quién tiene un rol personalizado <b>vigente</b> (activo y que hereda del rol de la cuenta), con el id de ese rol.
     * Agregado al contrato (§4.1): es la mitad «persona → rol» de la copia en memoria de la regla de disponibilidad.
     */
    Map<UserId, UUID> asignaciones();
}
