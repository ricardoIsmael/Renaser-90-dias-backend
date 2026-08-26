package com.renaser.os.users.application.services;

import com.renaser.os.users.application.ports.out.autenticacion.VerificadorIdentidadProveedor;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Indexa los adaptadores de login social por proveedor UNA SOLA VEZ, al construirse — mismo
 * patron y mismo motivo que {@code RegistroPoliticasHabito} (habits/domain/model/politica):
 * resolver con {@code .stream().filter(...).findFirst()} en cada login asignaria objetos
 * intermedios en un camino que corre en cada intento de sesion.
 *
 * <p>No vive en {@code domain/} como su analogo de habits porque referencia un puerto de
 * aplicacion ({@link VerificadorIdentidadProveedor}), no un tipo de dominio puro — {@code domain/}
 * no puede conocer los puertos {@code out} (CLAUDE.MD §5.1.2). Spring inyecta automaticamente
 * todos los {@code @Component} que implementan la interfaz como {@code List}; agregar Apple o
 * Facebook es una clase nueva, este registro no se toca.
 */
final class RegistroVerificadoresIdentidad {

    private final Map<ProveedorIdentidad, VerificadorIdentidadProveedor> porProveedor;

    RegistroVerificadoresIdentidad(List<VerificadorIdentidadProveedor> verificadores) {
        Map<ProveedorIdentidad, VerificadorIdentidadProveedor> indice = new EnumMap<>(ProveedorIdentidad.class);
        for (VerificadorIdentidadProveedor verificador : verificadores) {
            VerificadorIdentidadProveedor previo = indice.put(verificador.proveedor(), verificador);
            if (previo != null) {
                throw new IllegalStateException("Dos adaptadores declaran el mismo proveedor " + verificador.proveedor()
                        + ": " + previo.getClass().getName() + " y " + verificador.getClass().getName());
            }
        }
        this.porProveedor = Map.copyOf(indice);
    }

    /** @throws IllegalArgumentException si el proveedor pedido todavia no tiene adaptador registrado. */
    VerificadorIdentidadProveedor para(ProveedorIdentidad proveedor) {
        VerificadorIdentidadProveedor verificador = porProveedor.get(proveedor);
        if (verificador == null) {
            throw new IllegalArgumentException("Login con " + proveedor + " no esta disponible todavia");
        }
        return verificador;
    }
}
