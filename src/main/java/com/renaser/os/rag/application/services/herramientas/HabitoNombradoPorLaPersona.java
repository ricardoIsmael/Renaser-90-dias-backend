package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.FichaDeHabito;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Que habitos de la persona corresponden a como los nombro en el chat ("el ritual de mañana", "mi
 * jugo verde"), para {@code consultar_como_se_hace_habito} (D-236).
 *
 * <p>Compara contra los DOS nombres de la ficha, el suyo y el del programa: si renombro JUGO VERDE a
 * "Batido de papaya", "mi jugo verde" tiene que seguir encontrandolo (E-290). Sin tildes ni
 * mayusculas, y alcanza con que cada palabra que dijo empiece alguna palabra del titulo: "ritual de
 * mañana" encuentra "RITUAL TIERRA - AGUA - FUEGO (mañana)". Si no dijo nada util, no filtra.
 */
final class HabitoNombradoPorLaPersona {

    /** Palabras que no distinguen un habito de otro. */
    private static final Set<String> RELLENO = Set.of("a", "al", "como", "con", "de", "del", "el", "en", "hacer",
            "hago", "habito", "habitos", "la", "las", "lo", "los", "mi", "mis", "para", "por", "se", "su", "sus",
            "tu", "tus", "un", "una", "y");

    private HabitoNombradoPorLaPersona() {
    }

    /** Las fichas que coinciden, en el orden en que llegaron; vacia si nada coincide. */
    static List<FichaDeHabito> entre(List<FichaDeHabito> fichas, String comoLoNombro) {
        List<String> buscadas = palabrasUtiles(comoLoNombro);
        if (buscadas.isEmpty()) {
            return fichas;
        }
        return fichas.stream()
                .filter(ficha -> coincide(buscadas, ficha.tituloDelPrograma())
                        || coincide(buscadas, ficha.tituloPersonal()))
                .toList();
    }

    private static boolean coincide(List<String> buscadas, String titulo) {
        if (titulo == null) {
            return false;
        }
        List<String> delTitulo = palabras(titulo);
        return buscadas.stream().allMatch(buscada -> delTitulo.stream().anyMatch(p -> p.startsWith(buscada)));
    }

    private static List<String> palabrasUtiles(String texto) {
        return texto == null ? List.of() : palabras(texto).stream().filter(p -> !RELLENO.contains(p)).toList();
    }

    private static List<String> palabras(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return Arrays.stream(sinTildes.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(p -> !p.isEmpty())
                .toList();
    }
}
