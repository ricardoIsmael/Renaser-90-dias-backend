package com.renaser.os.phasecontracts.domain.model.animal;

/**
 * Formato y medidas de una imagen PNG o WebP, leídos de sus primeros bytes (sin abrirla entera, sin
 * ImageIO, que no lee WebP).
 */
public record CabeceraDeImagen(String formato, int ancho, int alto) {

    private static final int[] FIRMA_PNG = {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    /** @throws IllegalArgumentException si no es PNG ni WebP, o está truncada */
    public static CabeceraDeImagen de(byte[] b) {
        if (b != null && esPng(b)) {
            return new CabeceraDeImagen("png", entero32(b, 16), entero32(b, 20));
        }
        if (b != null && esWebp(b)) {
            return deWebp(b);
        }
        throw new IllegalArgumentException(ImagenDeAnimal.NO_ES_IMAGEN);
    }

    private static boolean esPng(byte[] b) {
        if (b.length < 24) {
            return false;
        }
        for (int i = 0; i < FIRMA_PNG.length; i++) {
            if ((b[i] & 0xFF) != FIRMA_PNG[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean esWebp(byte[] b) {
        return b.length >= 30 && texto(b, 0).equals("RIFF") && texto(b, 8).equals("WEBP");
    }

    private static CabeceraDeImagen deWebp(byte[] b) {
        return switch (texto(b, 12)) {
            case "VP8X" -> new CabeceraDeImagen("webp", 1 + entero24(b, 24), 1 + entero24(b, 27));
            case "VP8L" -> deWebpSinPerdida(b);
            case "VP8 " -> new CabeceraDeImagen("webp", entero16(b, 26) & 0x3FFF, entero16(b, 28) & 0x3FFF);
            default -> throw new IllegalArgumentException(ImagenDeAnimal.NO_ES_IMAGEN);
        };
    }

    private static CabeceraDeImagen deWebpSinPerdida(byte[] b) {
        long bits = (b[21] & 0xFFL) | (b[22] & 0xFFL) << 8 | (b[23] & 0xFFL) << 16 | (b[24] & 0xFFL) << 24;
        return new CabeceraDeImagen("webp", (int) (bits & 0x3FFF) + 1, (int) ((bits >> 14) & 0x3FFF) + 1);
    }

    private static String texto(byte[] b, int desde) {
        return new String(b, desde, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static int entero32(byte[] b, int d) {
        return (b[d] & 0xFF) << 24 | (b[d + 1] & 0xFF) << 16 | (b[d + 2] & 0xFF) << 8 | (b[d + 3] & 0xFF);
    }

    private static int entero24(byte[] b, int d) {
        return (b[d] & 0xFF) | (b[d + 1] & 0xFF) << 8 | (b[d + 2] & 0xFF) << 16;
    }

    private static int entero16(byte[] b, int d) {
        return (b[d] & 0xFF) | (b[d + 1] & 0xFF) << 8;
    }
}
