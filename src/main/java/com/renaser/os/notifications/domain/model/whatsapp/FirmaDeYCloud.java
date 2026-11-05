package com.renaser.os.notifications.domain.model.whatsapp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * La firma con que YCloud avisa al webhook (spec 041, FR-026): {@code YCloud-Signature: t=<segundos>,s=<hex>}, donde {@code s} es el HMAC-SHA256 de
 * {@code <t>.<cuerpo crudo>} con el secreto del endpoint tal cual (con su {@code whsec_}). Vale si coincide cualquiera de las firmas que traiga la
 * cabecera (rotacion del secreto) y la diferencia con ahora es de menos de {@link #MARGEN}. Es la unica puerta del webhook, que no tiene sesion.
 *
 * <p>Dominio puro: solo el JDK. La comparacion es en tiempo constante.
 */
public final class FirmaDeYCloud {

    /** Cinco minutos de margen, como los ejemplos de YCloud. */
    public static final Duration MARGEN = Duration.ofMinutes(5);
    private static final Pattern HEX_DE_SHA256 = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final String ALGORITMO = "HmacSHA256";

    private FirmaDeYCloud() {
    }

    /** {@code false} ante cualquier cosa que no cuadre: sin secreto, sin cabecera, sin {@code t}, vieja, futura o distinta. */
    public static boolean valida(byte[] cuerpo, String cabecera, String secreto, Instant ahora) {
        if (cuerpo == null || cabecera == null || secreto == null || secreto.isEmpty()) {
            return false;
        }
        String t = null;
        List<String> firmas = new ArrayList<>();
        for (String parte : cabecera.split(",")) {
            int igual = parte.indexOf('=');
            if (igual < 0) {
                continue;
            }
            String clave = parte.substring(0, igual).strip();
            String valor = parte.substring(igual + 1).strip();
            if (clave.equals("t")) {
                t = valor;
            } else if (clave.equals("s") && HEX_DE_SHA256.matcher(valor).matches()) {
                firmas.add(valor.toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (t == null || !t.matches("\\d{1,12}") || firmas.isEmpty() || !dentroDelMargen(Long.parseLong(t), ahora)) {
            return false;
        }
        byte[] esperada = HexFormat.of().parseHex(firmar(secreto, t, cuerpo));
        return firmas.stream().anyMatch(f -> MessageDigest.isEqual(HexFormat.of().parseHex(f), esperada));
    }

    /** La firma hexadecimal de un cuerpo; la usan las pruebas (y quien quiera simular a YCloud) para armar una cabecera valida. */
    public static String firmar(String secreto, String t, byte[] cuerpo) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            mac.update((t + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(cuerpo);
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular la firma", e);
        }
    }

    private static boolean dentroDelMargen(long segundos, Instant ahora) {
        return Math.abs(ahora.getEpochSecond() - segundos) <= MARGEN.toSeconds();
    }
}
