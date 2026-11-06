package com.renaser.os.community.domain.model.sobrenombre;

import com.renaser.os.community.domain.model.sobrenombre.SobrenombreRechazadoException.Motivo;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Spec 046 (D-331): el sobrenombre que reemplaza el nombre de una persona en el Muro. Las reglas de la forma viven aquí; las que
 * dependen de otras personas (repetido, 30 días) las revisa el servicio.
 *
 * @param texto       como lo escribió la persona (sin espacios sobrantes)
 * @param normalizado sin tildes, en minúsculas y con los espacios juntos: con él se comparan dos sobrenombres
 */
public record Sobrenombre(String texto, String normalizado) {

    public static final int MINIMO = 3;
    public static final int MAXIMO = 20;
    private static final Pattern PERMITIDOS = Pattern.compile("^[\\p{L}\\p{N} .\\-]+$");

    /** Valida la forma y las palabras prohibidas. Nunca devuelve un sobrenombre inválido. */
    public static Sobrenombre de(String crudo, Collection<String> prohibidasNormalizadas) {
        // Issue #100: NFC primero. Una tilde escrita como carácter combinable («e» + U+0301, que mandan algunos teclados y el
        // copiar y pegar) se ve igual que «é», pero sin esto no pasaba el patrón de letras y se rechazaba como «caracteres».
        String texto = crudo == null ? "" : Normalizer.normalize(crudo, Normalizer.Form.NFC).strip();
        if (texto.length() < MINIMO || texto.length() > MAXIMO) {
            throw new SobrenombreRechazadoException(Motivo.LARGO, "Usa de 3 a 20 letras.");
        }
        if (texto.matches(".*\\s{2,}.*")) {
            // Issue #100: antes decía «Usa de 3 a 20 letras.», que no explica el problema (el largo estaba bien).
            throw new SobrenombreRechazadoException(Motivo.LARGO, "Usa un solo espacio entre palabras.");
        }
        if (!PERMITIDOS.matcher(texto).matches()) {
            throw new SobrenombreRechazadoException(Motivo.CARACTERES, "Usa solo letras, números, espacios, puntos o guiones.");
        }
        String normalizado = normalizar(texto);
        String sinSeparadores = normalizado.replaceAll("[ .\\-]", "");
        for (String prohibida : prohibidasNormalizadas) {
            String p = normalizar(prohibida).replaceAll("[ .\\-]", "");
            if (!p.isEmpty() && sinSeparadores.contains(p)) {
                throw new SobrenombreRechazadoException(Motivo.PROHIBIDO, "Elige otro: ese no se puede usar.");
            }
        }
        return new Sobrenombre(texto, normalizado);
    }

    /** Sin tildes (la ñ queda como n), en minúsculas y con los espacios juntos. */
    public static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }
}
