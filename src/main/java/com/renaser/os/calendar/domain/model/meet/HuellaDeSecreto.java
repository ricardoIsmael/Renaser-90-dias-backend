package com.renaser.os.calendar.domain.model.meet;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** El SHA-256 de un valor secreto de un solo uso: lo que se guarda en lugar del {@code state} de OAuth (spec 040, FR-010). */
public final class HuellaDeSecreto {

    private HuellaDeSecreto() {
    }

    public static String de(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM", e);
        }
    }
}
