package com.renaser.os.notifications.domain.model.whatsapp;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Como se reconoce una baja de los recordatorios por WhatsApp (spec 041, FR-027): el toque del boton «No recibir más» (su carga lleva el id de la
 * entrega) o un texto que sea ENTERO «baja» o una de sus primas, sin contar mayusculas, tildes, signos ni espacios de mas. «¿Hay baja de precio?» es una
 * conversacion, no una baja. Dominio puro.
 *
 * <p>Que el texto responda a un mensaje nuestro (el {@code context.id} del proveedor) lo decide quien atiende el webhook: esta clase solo
 * reconoce las palabras.
 */
public final class PalabrasDeBaja {

    private static final Set<String> BAJAS = Set.of("baja", "darme de baja", "dar de baja", "stop", "no mas",
            "no mas mensajes", "no recibir mas", "cancelar recordatorios");
    private static final Pattern CARGA = Pattern.compile(
            "^BAJA:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$", Pattern.CASE_INSENSITIVE);
    private static final String PREFIJO_DE_LA_CARGA = "BAJA:";

    private PalabrasDeBaja() {
    }

    /** {@code true} si el texto entero es una baja. */
    public static boolean esBaja(String texto) {
        if (texto == null) {
            return false;
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String limpio = sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\s]", " ").replaceAll("\\s+", " ").strip();
        return BAJAS.contains(limpio);
    }

    /** La carga que lleva el boton de respuesta rapida de una entrega: {@code BAJA:<id>}. */
    public static String cargaDelBoton(UUID entregaId) {
        return PREFIJO_DE_LA_CARGA + entregaId;
    }

    /** El id de la entrega si la carga es de un boton nuestro; una carga ajena (otro boton de la cuenta) no lo es. */
    public static java.util.Optional<UUID> entregaDeLaCarga(String carga) {
        if (carga == null) {
            return java.util.Optional.empty();
        }
        Matcher m = CARGA.matcher(carga.strip());
        return m.matches() ? java.util.Optional.of(UUID.fromString(m.group(1))) : java.util.Optional.empty();
    }
}
