package com.renaser.os.plataforma.domain.model.despliegue;

import com.renaser.os.shared.domain.UserId;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * En qué cubeta (0 a 99) cae una persona para una funcionalidad (spec 002, FR-018; contrato fase 2 §3.2): los primeros
 * 4 bytes del SHA-256 de {@code clave + ":" + usuarioId} (el UUID en minúsculas, como lo escribe {@code UUID}), leídos
 * como entero sin signo en orden de red, módulo 100.
 *
 * <p>Es una función pura: la misma persona cae siempre en la misma cubeta para la misma funcionalidad —en cualquier
 * instancia, en cualquier teléfono, después de cualquier reinicio— y no hay nada que guardar ni que asignar (SC-004).
 * Una persona está dentro de un despliegue al P % si su cubeta es menor que P, así que subir el porcentaje solo suma
 * cubetas: nadie que la tenía la pierde.
 *
 * <p>La clave entra en el hash a propósito: con el id solo, el mismo 5 % de personas probaría todo lo nuevo.
 */
public final class Cubeta {

    public static final int CANTIDAD = 100;

    private Cubeta() {
    }

    public static int de(String clave, UserId persona) {
        byte[] hash = sha256((clave + ":" + persona.value()).getBytes(StandardCharsets.UTF_8));
        long sinSigno = Integer.toUnsignedLong(ByteBuffer.wrap(hash, 0, Integer.BYTES).getInt());
        return (int) (sinSigno % CANTIDAD);
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
