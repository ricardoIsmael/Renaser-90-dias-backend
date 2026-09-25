package com.renaser.os.rag.domain.model.propuesta;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * SHA-256 de la forma canonica de una accion propuesta: herramienta + argumentos ordenados por
 * nombre. Es lo que permite verificar, al confirmar, que se va a ejecutar EXACTAMENTE lo que se le
 * mostro a la persona (columna {@code argumentos_hash} de V63).
 *
 * <p><b>Por que cada pieza va con su largo delante.</b> Concatenar "clave=valor" a secas deja que
 * dos mapas distintos den el mismo texto ({@code {"a":"b=c"}} y {@code {"a=b":"c"}}). Con el largo
 * prefijado la forma canonica es inyectiva y el hash no se puede provocar con separadores.
 *
 * <p>Solo JDK ({@code java.security}): el dominio sigue libre de frameworks.
 */
public record HuellaArgumentos(String valor) {

    public HuellaArgumentos {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("La huella de los argumentos es obligatoria");
        }
    }

    public static HuellaArgumentos de(String herramienta, Map<String, String> argumentos) {
        Objects.requireNonNull(herramienta, "herramienta es obligatoria");
        Objects.requireNonNull(argumentos, "argumentos es obligatorio");
        StringBuilder canonica = new StringBuilder();
        agregar(canonica, herramienta);
        new TreeMap<>(argumentos).forEach((nombre, valor) -> {
            agregar(canonica, nombre);
            agregar(canonica, valor);
        });
        return new HuellaArgumentos(sha256(canonica.toString()));
    }

    public boolean coincideCon(String herramienta, Map<String, String> argumentos) {
        return equals(de(herramienta, argumentos));
    }

    private static void agregar(StringBuilder canonica, String pieza) {
        String texto = pieza == null ? "" : pieza;
        canonica.append(texto.length()).append(':').append(texto).append(';');
    }

    private static String sha256(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Toda JVM esta obligada a traer SHA-256 (javadoc de MessageDigest): no pasa.
            throw new IllegalStateException("La JVM no trae SHA-256", e);
        }
    }
}
