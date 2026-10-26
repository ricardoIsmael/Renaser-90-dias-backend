package com.renaser.os.users.application.ports.out.rolpersonalizado;

import com.renaser.os.users.domain.model.rolpersonalizado.RolPersonalizado;

import java.util.UUID;

/** Escribir roles personalizados (V115, D-272). Todo dentro de la transacción del caso de uso. */
public interface GuardarRolesPersonalizadosPort {

    /**
     * Serializa las escrituras de roles personalizados hasta el fin de la transacción: el nombre libre y el máximo de
     * activos miran otras filas, y dos altas a la vez no pueden colarse juntas.
     */
    void bloquearCambios();

    void insertar(RolPersonalizado rol);

    /** @return {@code false} si la versión guardada ya no es {@code versionEsperada} (otra persona lo cambió) */
    boolean actualizar(RolPersonalizado rol, long versionEsperada);

    /** Borra el rol; antes suelta la cuenta de prueba de «ver como» que lo lleve (D-273), para que la FK no lo frene. */
    void borrar(UUID id);

    /**
     * Pasa a todas las cuentas de {@code origen} a {@code destino} ({@code null}: sin rol personalizado), menos la cuenta
     * de prueba de «ver como» (D-273), que sigue siendo la de {@code origen}.
     *
     * @return cuántas cuentas se movieron
     */
    int reemplazarAsignaciones(UUID origen, UUID destino);
}
