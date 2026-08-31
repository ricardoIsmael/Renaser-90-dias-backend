package com.renaser.os.users.domain.model.accountrequest;

import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;

import java.util.Objects;

/**
 * Por que puerta entro una solicitud de alta cuando la abrio un proveedor social
 * (docs/MODULO_AUTH.md §6.4). Es el dato que faltaba y que causaba A-7: el {@code sub} del
 * proveedor se verificaba al iniciar el alta y se perdia ahi mismo, asi que al aprobar ya no
 * habia con que crear la {@code IdentidadExterna} — y esa persona no podia volver a entrar
 * nunca por el mismo proveedor.
 *
 * <p>Los dos campos viajan SIEMPRE juntos: un proveedor sin sujeto no identifica a nadie, y un
 * sujeto sin proveedor es ambiguo (dos proveedores distintos pueden emitir el mismo string). El
 * constructor lo hace imposible; la migracion V12 repite la misma regla como CHECK en la base.
 *
 * <p>Una solicitud de alta por formulario NO tiene origen social: el campo es null en
 * {@code AccountRequest}, y eso es un estado valido, no un dato faltante.
 */
public record OrigenSocial(ProveedorIdentidad proveedor, String sujetoProveedor) {

    public OrigenSocial {
        Objects.requireNonNull(proveedor, "proveedor es obligatorio");
        if (sujetoProveedor == null || sujetoProveedor.isBlank()) {
            throw new IllegalArgumentException(
                    "sujetoProveedor es obligatorio: es la clave de identidad del proveedor");
        }
    }

    /**
     * El sujeto del proveedor es un identificador opaco que correlaciona a una persona entre
     * sistemas: no va al log, igual que {@code IdentidadExterna.toString()}.
     */
    @Override
    public String toString() {
        return "OrigenSocial[" + proveedor + "]";
    }
}
