package com.renaser.os.users.application.ports.out.autenticacion;

import com.renaser.os.users.domain.model.identidadexterna.IdentidadExterna;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;

import java.util.Optional;

public interface LoadIdentidadExternaPort {

    /**
     * La UNICA forma de resolver una identidad social: por {@code (proveedor, sujeto)}, nunca por
     * email (docs/MODULO_AUTH.md §6.4 — regla de seguridad no negociable, vincular por email
     * permitiria que quien registre una cuenta social con el correo de un aprendiz existente se
     * apodere de ella).
     */
    Optional<IdentidadExterna> porProveedorYSujeto(ProveedorIdentidad proveedor, String sujetoProveedor);
}
