package com.renaser.os.rag.domain.model.memoria;

import com.renaser.os.rag.domain.model.conversacion.FiltroDeIdentificadores;

import java.text.Normalizer;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lo que devolvio el modelo al compactar una conversacion (D-167): un resumen nuevo y los recuerdos
 * que quedan, por categoria. Antes de guardarse pasa por {@link #textosQueQuedan} y
 * {@link #resumenSaneado()}, que no confian en el modelo: el prompt de compactacion ya pide todo
 * esto, y esta es la segunda capa.
 *
 * @param recuerdos solo las categorias que el modelo devolvio. Una que falta no significa "olvidar
 *                  todo lo de esa categoria": se conserva lo que habia (una respuesta a medias no
 *                  puede vaciar la memoria de nadie)
 */
public record Compactacion(String resumen, Map<CategoriaDeRecuerdo, List<String>> recuerdos) {

    /** Cuantos recuerdos por categoria: los mas utiles, no una biografia. */
    public static final int MAXIMO_POR_CATEGORIA = 6;

    /**
     * Lo emocional y la salud no se guardan (dato sensible, Ley 29733; decision del dueno del
     * 2026-09-25). Si el modelo igual los mete, se descartan aca. Son raices, sin tildes: se comparan
     * contra el texto normalizado. "Pastilla" y "terapia" solas NO estan: son nombres de habitos
     * del programa (Pastilla Renacer, Audioterapia). "Sueno" solo tampoco: "su sueno es tener un
     * negocio" es una meta; lo que se descarta es no poder dormir.
     *
     * <p>Corregido 2026-09-25 (E-273): la lista no tenia preocupaciones, estres, cansancio ni el
     * sueno, y el primer resumen real guardo "la dificultad para conciliar el sueno debido a las
     * preocupaciones laborales". Se sumaron esas raices.
     */
    private static final List<String> SENSIBLES = List.of("ansiedad", "ansios", "depres", "suicid", "autolesi",
            "psiquiatr", "psicolog", "medicament", "diagnost", "enfermedad", "trastorno", "panico", "trauma",
            "llora", "llanto", "tristeza", "angusti", "crisis", "adiccion", "embaraz", "violencia",
            "preocup", "estres", "insomn", "agobi", "frustr", "miedo", "culpa", "desanim", "desmotiv", "animo",
            "sentimient", "se siente", "me siento", "cansad", "cansanci", "agotad", "fracas", "falland",
            "conciliar el sueno", "dormir mal", "duerme mal", "no puede dormir", "no pudo dormir");

    public Compactacion {
        resumen = resumen == null ? "" : resumen.strip();
        recuerdos = recuerdos == null ? Map.of() : Map.copyOf(recuerdos);
    }

    /** El resumen listo para guardar, o vacio si no sirve (sin texto, o con algo sensible). */
    public String resumenSaneado() {
        String limpio = FiltroDeIdentificadores.taparEn(resumen);
        if (limpio.isBlank() || esSensible(limpio)) {
            return "";
        }
        return limpio.length() <= MemoriaDeRenasia.LARGO_MAXIMO_DEL_RESUMEN ? limpio
                : limpio.substring(0, MemoriaDeRenasia.LARGO_MAXIMO_DEL_RESUMEN).strip();
    }

    /**
     * Lo que queda en cada categoria: lo que devolvio el modelo, saneado (sin repetidos, sin ids,
     * sin nada sensible, en su largo y en su cantidad); si no devolvio esa categoria, lo que ya habia.
     */
    public Map<CategoriaDeRecuerdo, List<String>> textosQueQuedan(MemoriaDeRenasia actual) {
        Map<CategoriaDeRecuerdo, List<String>> salida = new EnumMap<>(CategoriaDeRecuerdo.class);
        Map<CategoriaDeRecuerdo, List<Recuerdo>> antes = actual.porCategoria();
        for (CategoriaDeRecuerdo categoria : CategoriaDeRecuerdo.values()) {
            List<String> textos = recuerdos.containsKey(categoria) ? saneados(recuerdos.get(categoria))
                    : antes.getOrDefault(categoria, List.of()).stream().map(Recuerdo::texto).toList();
            if (!textos.isEmpty()) {
                salida.put(categoria, textos);
            }
        }
        return salida;
    }

    private static List<String> saneados(List<String> textos) {
        Set<String> vistos = new LinkedHashSet<>();
        for (String texto : Objects.requireNonNullElse(textos, List.<String>of())) {
            String limpio = texto == null ? "" : texto.strip();
            boolean sirve = !limpio.isEmpty() && limpio.length() <= Recuerdo.LARGO_MAXIMO && !esSensible(limpio)
                    && FiltroDeIdentificadores.taparEn(limpio).equals(limpio);
            if (sirve && vistos.size() < MAXIMO_POR_CATEGORIA) {
                vistos.add(limpio);
            }
        }
        return List.copyOf(vistos);
    }

    static boolean esSensible(String texto) {
        String normal = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return SENSIBLES.stream().anyMatch(normal::contains);
    }
}
