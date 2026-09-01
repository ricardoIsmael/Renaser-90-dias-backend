package com.renaser.os.users.application.ports.out.autenticacion;

import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;

/**
 * La identidad que {@code AutenticacionSocialService} ya verifico contra el proveedor
 * (Google/Apple/Facebook), retenida hasta que la persona confirma el formulario de alta
 * (docs/MODULO_AUTH.md §6.10, D-65). Existe porque el {@code code} de OAuth es de un solo uso y
 * la app no conoce el correo/nombre hasta que el backend lo canjea: sin retener esta identidad
 * en algun lado, no hay forma de prellenar un formulario de confirmacion sin gastar el `code`
 * antes de tiempo.
 *
 * <p>{@code email} y {@code sujetoProveedor} viven SOLO aca adentro: nunca vuelven a viajar por
 * HTTP en el paso de confirmacion ({@code POST /auth/social/complete}) — es literalmente lo que
 * este registro existe para evitar (mismo blindaje por compilador que el `role` ausente del
 * alta publica, CLAUDE.MD §5.3.3).
 */
public record RegistroPendienteSocial(ProveedorIdentidad proveedor, String sujetoProveedor, String email,
                                       String fullName) {

    public RegistroPendienteSocial {
        if (proveedor == null) {
            throw new IllegalArgumentException("proveedor es obligatorio");
        }
        if (sujetoProveedor == null || sujetoProveedor.isBlank()) {
            throw new IllegalArgumentException("sujetoProveedor es obligatorio");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email es obligatorio");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("fullName es obligatorio");
        }
    }

    /**
     * El sujeto del proveedor es un identificador opaco que correlaciona a una persona entre
     * sistemas: no va al log, igual que {@code OrigenSocial.toString()} e
     * {@code IdentidadExterna.toString()}. El email tampoco: es un dato personal.
     */
    @Override
    public String toString() {
        return "RegistroPendienteSocial[proveedor=" + proveedor + "]";
    }
}
