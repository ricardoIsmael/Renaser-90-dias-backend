package com.renaser.os.users.application.ports.in.emergencia;

import com.renaser.os.shared.domain.UserId;

/**
 * Al cambiar el día del programa de alguien con un pedido de emergencia abierto, el pedido queda resuelto con
 * ese día (D-244). Lo llama «Cambiar día del programa» dentro de su transacción: o quedan las dos cosas, o
 * ninguna. Sin pedido abierto no hace nada.
 */
public interface ResolverEmergenciaAlCambiarDiaUseCase {

    void alCambiarDia(UserId aprendizId, UserId actorId, int diaNuevo);
}
