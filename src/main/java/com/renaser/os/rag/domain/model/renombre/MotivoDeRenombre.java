package com.renaser.os.rag.domain.model.renombre;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Si el motivo de un renombre lo dijo la persona o lo puso el modelo (D-236, E-466).
 *
 * <p>El renombre de {@code habits} exige un motivo escrito (D-133): queda registrado por que se
 * cambio. En la prueba con IA real, a «quiero que mi jugo verde se llame batido de papaya» el modelo
 * propuso con motivo «Prefiero llamarlo batido de papaya», y despues con «porque el apio me cae mal»,
 * que era el ejemplo de la descripcion de la herramienta. Ninguna instruccion lo freno, asi que la
 * regla se hace cumplir aca.
 *
 * <p>La regla: cada palabra con contenido del motivo aparece en lo que la persona escribio hace poco
 * (sin tildes ni mayusculas, en cualquier orden, prefijo de palabra: «cae» cubre «caen»), y al menos
 * una no es del nombre nuevo ni de pedir el cambio: «quiero que se llame X» no es un motivo. Es una
 * guarda contra motivos inventados, no un juicio sobre si el motivo es bueno.
 */
public final class MotivoDeRenombre {

    /** Palabras que no dicen nada por si solas. */
    private static final Set<String> RELLENO = Set.of("a", "al", "algo", "asi", "como", "con", "de", "del", "el",
            "en", "es", "esta", "la", "las", "lo", "los", "me", "mi", "mis", "muy", "para", "pero", "por", "porque",
            "pues", "que", "se", "si", "su", "un", "una", "y", "ya", "yo");

    /** Palabras de PEDIR el cambio: si el motivo solo trae estas y el nombre nuevo, no es un motivo. */
    private static final Set<String> DE_PEDIR_EL_CAMBIO = Set.of("cambiar", "cambie", "cambies", "cambialo",
            "cambio", "habito", "llama", "llame", "llamar", "llamarlo", "llamarle", "nombre", "poner", "ponerle",
            "ponle", "quiero", "quisiera", "renombrar");

    private MotivoDeRenombre() {
    }

    /**
     * @param motivo              el que mando el modelo, ya recortado
     * @param nuevoNombre         el nombre que se propone
     * @param escritoPorLaPersona sus mensajes recientes, tal cual los escribio
     */
    public static boolean loDijoLaPersona(String motivo, String nuevoNombre, List<String> escritoPorLaPersona) {
        List<String> delMotivo = palabras(motivo).stream().filter(p -> !RELLENO.contains(p)).toList();
        List<String> dichas = escritoPorLaPersona.stream().flatMap(texto -> palabras(texto).stream()).toList();
        List<String> delNombre = palabras(nuevoNombre);
        boolean todasDichas = delMotivo.stream().allMatch(p -> dichas.stream().anyMatch(d -> d.startsWith(p)));
        boolean algunaEsRazon = delMotivo.stream()
                .anyMatch(p -> !delNombre.contains(p) && !DE_PEDIR_EL_CAMBIO.contains(p));
        return !delMotivo.isEmpty() && todasDichas && algunaEsRazon;
    }

    private static List<String> palabras(String texto) {
        if (texto == null) {
            return List.of();
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return Arrays.stream(sinTildes.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(p -> !p.isEmpty())
                .toList();
    }
}
