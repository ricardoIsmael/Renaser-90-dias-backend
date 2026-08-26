package com.renaser.os.users.domain.model.identidadexterna;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Vinculo entre un usuario nuestro y su cuenta en un proveedor social (docs/MODULO_AUTH.md §2,
 * tabla {@code identidades_externas} de la migracion V3). Agregado propio, distinto de
 * {@code User}: es 1:N real (un usuario puede vincular Google Y Apple Y Facebook a la vez), y su
 * identidad natural es {@code (proveedor, sujetoProveedor)} — nunca el email (§6.4).
 *
 * <p>Sin comportamiento mas alla de su construccion: una vez vinculada, una identidad externa no
 * cambia (no hay "renombrar" ni "actualizar sujeto"). Por eso es un record, igual que
 * {@link com.renaser.os.users.domain.model.user.Credencial}, y no una clase con Lombok.
 */
public record IdentidadExterna(ProveedorIdentidad proveedor, String sujetoProveedor, UserId usuarioId,
                                String emailProveedor, Instant vinculadaEn) {

    public IdentidadExterna {
        Objects.requireNonNull(proveedor, "proveedor es obligatorio");
        if (sujetoProveedor == null || sujetoProveedor.isBlank()) {
            throw new IllegalArgumentException("sujetoProveedor es obligatorio: es la clave de identidad del proveedor");
        }
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        Objects.requireNonNull(vinculadaEn, "vinculadaEn es obligatorio");
        // emailProveedor SI puede ser null/blank: es informativo (§2.2), nunca se usa para resolver identidad.
    }

    /** Vincular por primera vez. {@code emailProveedor} es informativo, nunca se usa para autenticar. */
    public static IdentidadExterna vincular(ProveedorIdentidad proveedor, String sujetoProveedor, UserId usuarioId,
                                             String emailProveedor, Clock clock) {
        return new IdentidadExterna(proveedor, sujetoProveedor, usuarioId, emailProveedor, clock.now());
    }

    /** Solo para el adaptador de persistencia: reconstruye un vinculo ya existente. */
    public static IdentidadExterna rehydrate(ProveedorIdentidad proveedor, String sujetoProveedor, UserId usuarioId,
                                              String emailProveedor, Instant vinculadaEn) {
        return new IdentidadExterna(proveedor, sujetoProveedor, usuarioId, emailProveedor, vinculadaEn);
    }

    @Override
    public String toString() {
        return "IdentidadExterna[" + proveedor + ", usuarioId=" + usuarioId + "]";
    }
}
