package com.renaser.os.community.domain.model.publicacion;

import java.util.Optional;

/**
 * La miniatura de una foto del Muro (spec 049, D-339): la arma la Lambda {@code renaser-miniaturas-muro} cuando la foto llega a
 * S3 y la deja con un nombre fijo, así el backend la firma por convención sin enterarse de nada: {@code muro/fotos/<autor>/<id>}
 * → {@code muro/miniaturas/<autor>/<id>.webp}. Las fotos que no están bajo {@code muro/fotos/} (evidencias publicadas desde otro
 * módulo, videos) no tienen miniatura.
 */
public final class MiniaturaDeFoto {

    static final String PREFIJO_FOTOS = "muro/fotos/";
    static final String PREFIJO_MINIATURAS = "muro/miniaturas/";

    private MiniaturaDeFoto() {
    }

    public static Optional<String> rutaDe(String rutaDeLaFoto) {
        if (rutaDeLaFoto == null || !rutaDeLaFoto.startsWith(PREFIJO_FOTOS) || rutaDeLaFoto.length() == PREFIJO_FOTOS.length()) {
            return Optional.empty();
        }
        return Optional.of(PREFIJO_MINIATURAS + rutaDeLaFoto.substring(PREFIJO_FOTOS.length()) + ".webp");
    }
}
