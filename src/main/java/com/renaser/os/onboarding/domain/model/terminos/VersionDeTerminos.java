package com.renaser.os.onboarding.domain.model.terminos;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Una versión de los términos de una formación (spec 027): el borrador que Administración edita, o una publicada, que es una foto inmutable del texto con su
 * número, su fecha, su autor, su nota y si pidió que quienes ya aceptaron acepten de nuevo.
 *
 * @param numero        el de la versión dentro de su formación (1, 2, 3…); {@code null} en el borrador
 * @param baseNumero    de qué versión publicada partió el borrador ({@code null} si no había ninguna): con él se detecta que otra persona publicó mientras se editaba (FR-008)
 * @param publicadaPor  quien la publicó; {@code null} si esa cuenta se borró
 */
public record VersionDeTerminos(UUID id, Formacion formacion, EstadoDeVersion estado, Integer numero, ContenidoDeTerminos contenido, String nota,
                                boolean pideNuevaAceptacion, Integer baseNumero, Instant publicadaEn, UserId publicadaPor, Instant actualizadaEn) {

    public static VersionDeTerminos borrador(UUID id, Formacion formacion, ContenidoDeTerminos contenido, Integer baseNumero, Instant ahora) {
        return new VersionDeTerminos(id, formacion, EstadoDeVersion.BORRADOR, null, contenido, null, false, baseNumero, null, null, ahora);
    }

    public boolean estaPublicada() {
        return estado == EstadoDeVersion.PUBLICADA;
    }

    /** El mismo borrador con otro texto (guardar). */
    public VersionDeTerminos conContenido(ContenidoDeTerminos nuevo, Instant ahora) {
        return new VersionDeTerminos(id, formacion, estado, numero, nuevo, nota, pideNuevaAceptacion, baseNumero, publicadaEn, publicadaPor, ahora);
    }

    /** Pasa a ser la versión {@code numero} de su formación. */
    public VersionDeTerminos publicada(int numeroNuevo, String notaDelCambio, boolean pideNueva, UserId quien, Instant ahora) {
        if (estaPublicada()) {
            throw new IllegalStateException("Esa versión ya está publicada.");
        }
        return new VersionDeTerminos(id, formacion, EstadoDeVersion.PUBLICADA, numeroNuevo, contenido, notaLimpia(notaDelCambio), pideNueva, baseNumero, ahora, quien,
                ahora);
    }

    private static String notaLimpia(String nota) {
        String limpia = nota == null ? "" : nota.strip();
        if (limpia.length() > 500) {
            throw new IllegalArgumentException("La nota del cambio puede tener hasta 500 caracteres.");
        }
        return limpia.isEmpty() ? null : limpia;
    }
}
