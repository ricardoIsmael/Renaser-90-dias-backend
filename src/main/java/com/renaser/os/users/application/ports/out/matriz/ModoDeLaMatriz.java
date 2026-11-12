package com.renaser.os.users.application.ports.out.matriz;

import java.util.Locale;
import java.util.Optional;

/**
 * Qué hace el interceptor de permisos con la matriz estricta de MENTOR y TRAINEE_GUIDE (spec 050, tramo H, FR-041).
 * Nace en {@link #SOMBRA}: evalúa la matriz, anota lo que habría denegado y deja pasar. Con {@link #APLICAR} deniega con el
 * 403 de siempre.
 */
public enum ModoDeLaMatriz {

    /** Evalúa y anota «habría denegado», pero el pedido pasa: nunca responde 403 por la matriz. Es lo que se mide en uso real. */
    SOMBRA,

    /** Evalúa y, si el permiso no está en la lista aprobada, responde 403 {@code SIN_PERMISO}. */
    APLICAR;

    /** El modo que dice un valor escrito a mano (Redis, la consola), o vacío si no es uno: un error de tipeo no enciende nada. */
    public static Optional<ModoDeLaMatriz> interpretar(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(valor.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException noEsUnModo) {
            return Optional.empty();
        }
    }
}
