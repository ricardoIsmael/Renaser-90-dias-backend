package com.renaser.os.community.domain.model.testimonio;

import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * Un testimonio de la comunidad (tabla `testimonios`), a mano o promovido desde una
 * publicacion del Muro. Traduccion 1:1 de `features/testimonios/service.ts`.
 *
 * <p>`usuarioId` y `publicacionMuroId` son opcionales: un testimonio a mano no tiene
 * cuenta asociada (formulario publico, `createTestimonio(userId=null, ...)`,
 * testimonios/repository.ts:24-37). `fotoEventoRuta` es una RUTA de storage, no una URL
 * (P-03 del baseline) — antes `fotoEventoUrl`.
 *
 * <p>El codigo viejo SIEMPRE crea con `isFeatured = true` (no hay forma de retirar un
 * testimonio en ningun endpoint) — `destacado` nace en `true` por la misma razon
 * (CM-11, docs/MODULO_COMMUNITY.md sec. 5). `retirar()`/`destacar()` quedan disponibles
 * para una futura moderacion administrativa que el codigo viejo no ofrecia.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class Testimonio {

    private final TestimonioId id;
    private final UserId usuarioId;
    private final PublicacionId publicacionMuroId;
    private final String nombre;
    private final String rolTexto;
    private final String avatarUrl;
    private final String fotoEventoRuta;
    private final String texto;
    private final int estrellas;
    private boolean destacado;
    private final Instant creadoEn;

    public static Testimonio crear(UserId usuarioId, PublicacionId publicacionMuroId, String nombre,
                                    String rolTexto, String avatarUrl, String fotoEventoRuta, String texto,
                                    int estrellas, Instant ahora) {
        requireNombreValido(nombre);
        requireTextoValido(texto);
        requireEstrellasValidas(estrellas);
        String rolEfectivo = (rolTexto == null || rolTexto.isBlank()) ? "Miembro de la comunidad" : rolTexto;
        return new Testimonio(TestimonioId.newId(), usuarioId, publicacionMuroId, nombre, rolEfectivo, avatarUrl,
                fotoEventoRuta, texto, estrellas, true, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static Testimonio rehydrate(TestimonioId id, UserId usuarioId, PublicacionId publicacionMuroId,
                                        String nombre, String rolTexto, String avatarUrl, String fotoEventoRuta,
                                        String texto, int estrellas, boolean destacado, Instant creadoEn) {
        return new Testimonio(id, usuarioId, publicacionMuroId, nombre, rolTexto, avatarUrl, fotoEventoRuta, texto,
                estrellas, destacado, creadoEn);
    }

    public void retirar() {
        this.destacado = false;
    }

    public void destacar() {
        this.destacado = true;
    }

    private static void requireNombreValido(String nombre) {
        if (nombre == null || nombre.trim().length() < 2) {
            throw new IllegalArgumentException("El nombre debe tener al menos 2 caracteres");
        }
    }

    private static void requireTextoValido(String texto) {
        if (texto == null || texto.trim().length() < 5) {
            throw new IllegalArgumentException("El texto debe tener al menos 5 caracteres");
        }
    }

    private static void requireEstrellasValidas(int estrellas) {
        if (estrellas < 1 || estrellas > 5) {
            throw new IllegalArgumentException("estrellas debe estar entre 1 y 5");
        }
    }

    @Override
    public String toString() {
        return "Testimonio[" + id + ", " + nombre + "]";
    }
}
