package com.renaser.os.users.application.ports.out.autenticacion;

import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;

/**
 * Puerto polimorfico: un adaptador por proveedor de login social (docs/MODULO_AUTH.md §6.2).
 * Agregar un proveedor nuevo es una clase {@code @Component} nueva que implementa esta interfaz
 * — ni el caso de uso que compone el login ni el dominio se enteran (Open/Closed, CLAUDE.MD
 * §5.4.8). Mismo patron ya usado para {@code PoliticaHabito} en {@code habits/domain/model/politica}.
 */
public interface VerificadorIdentidadProveedor {

    ProveedorIdentidad proveedor();

    /**
     * Canjea el {@code code} de OAuth por la identidad verificada del usuario en el proveedor.
     *
     * @throws com.renaser.os.shared.domain.IdentidadProveedorInvalidaException si el proveedor
     *         rechaza el intercambio o la identidad no se puede verificar
     */
    IdentidadVerificada verificar(CanjeCodigoCommand command);
}
