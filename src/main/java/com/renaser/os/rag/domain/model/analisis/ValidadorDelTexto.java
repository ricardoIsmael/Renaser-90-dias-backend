package com.renaser.os.rag.domain.model.analisis;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que un texto del análisis tiene que cumplir para mostrarse (spec 031, FR-008, FR-009 y SC-002). Pura: recibe el texto y las cifras
 * que se calcularon y dice qué incumple. Un texto con un incumplimiento <b>no se guarda ni se muestra</b>: cuenta como un intento
 * fallido.
 *
 * <ol>
 *   <li><b>Cifras</b>: todo número que aparece en el texto tiene que estar entre los calculados ({@code numerosPermitidos}); la IA
 *       interpreta, no calcula (FR-002). Los números se leen con dígitos; «6,4» y «6.4» son el mismo.</li>
 *   <li><b>Sin diagnóstico</b>: ni nombres de cuadros clínicos ni palabras de diagnóstico (FR-008).</li>
 *   <li><b>Sin compararse con otras personas</b> ni hablar de ellas.</li>
 *   <li><b>Sin citar frases</b> de lo que la persona escribió (031-3 A): ninguna comilla en «Cómo estuviste».</li>
 *   <li><b>Sin pasarse de emojis</b>: tres como máximo.</li>
 * </ol>
 *
 * <p>No puede saber si un nombre propio es de otra persona ni si un tono es cálido: eso lo pide el prompt. Lo que sí puede hacer es
 * negarse a lo que se mide.
 */
public final class ValidadorDelTexto {

    public static final int MAXIMO_DE_EMOJIS = 3;

    private static final Pattern NUMERO = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final Pattern COMILLAS = Pattern.compile("[«»“”\"]");
    private static final Set<String> CUADROS_CLINICOS = Set.of("depresion", "depresiv", "ansiedad", "ansios", "trastorno", "bipolar",
            "esquizofren", "burnout", "estres postraumatico", "tdah", "diagnostic", "psicolog", "psiquiatr", "suicid", "autolesi",
            "anorexi", "bulimi", "adiccion", "patolog", "enfermedad mental", "crisis de panico", "ataque de panico");
    private static final Set<String> OTRAS_PERSONAS = Set.of("otros aprendices", "otras personas", "tus companeros", "tus companeras",
            "el resto del grupo", "los demas aprendices", "otros participantes", "la mayoria de las personas", "promedio del grupo");

    private ValidadorDelTexto() {
    }

    /**
     * @param numerosPermitidos los números que el texto puede nombrar, normalizados ({@code "12"}, {@code "6.4"})
     * @return lo que incumple, en palabras para el registro técnico (sin citar el texto); vacío si se puede mostrar
     */
    public static List<String> incumplimientos(TextoDelAnalisis texto, Set<String> numerosPermitidos) {
        List<String> problemas = new ArrayList<>();
        problemas.addAll(cifrasQueNoSon(texto, numerosPermitidos));
        String todo = sinTildes(String.join("\n", texto.partes()));
        if (CUADROS_CLINICOS.stream().anyMatch(todo::contains)) {
            problemas.add("nombra un cuadro clínico o diagnostica");
        }
        if (OTRAS_PERSONAS.stream().anyMatch(todo::contains)) {
            problemas.add("compara con otras personas");
        }
        if (COMILLAS.matcher(texto.comoEstuviste()).find()) {
            problemas.add("cita frases en «Cómo estuviste»");
        }
        if (contarEmojis(String.join("\n", texto.partes())) > MAXIMO_DE_EMOJIS) {
            problemas.add("tiene más de " + MAXIMO_DE_EMOJIS + " emojis");
        }
        return List.copyOf(problemas);
    }

    /** Los números de un texto, normalizados como los permitidos (coma decimal a punto, sin ceros de más). */
    public static Set<String> numerosEn(String texto) {
        Set<String> numeros = new java.util.LinkedHashSet<>();
        Matcher encontrado = NUMERO.matcher(texto == null ? "" : texto);
        while (encontrado.find()) {
            numeros.add(normalizar(encontrado.group()));
        }
        return numeros;
    }

    /** {@code "6,40"} y {@code "6.4"} son el mismo número; {@code "07"} es el {@code "7"}. */
    public static String normalizar(String numero) {
        String punto = numero.replace(',', '.');
        if (punto.contains(".")) {
            String sinCeros = punto.replaceAll("0+$", "");
            punto = sinCeros.endsWith(".") ? sinCeros.substring(0, sinCeros.length() - 1) : sinCeros;
        }
        String entera = punto.contains(".") ? punto.substring(0, punto.indexOf('.')) : punto;
        String resto = punto.contains(".") ? punto.substring(punto.indexOf('.')) : "";
        String sinCerosAdelante = entera.replaceFirst("^0+(?=\\d)", "");
        return sinCerosAdelante + resto;
    }

    private static List<String> cifrasQueNoSon(TextoDelAnalisis texto, Set<String> permitidos) {
        List<String> ajenos = new ArrayList<>();
        for (String parte : texto.partes()) {
            numerosEn(parte).stream().filter(numero -> !permitidos.contains(numero)).forEach(numero -> ajenos.add("una cifra que no es "
                    + "una de las calculadas"));
        }
        return ajenos.stream().distinct().toList();
    }

    private static String sinTildes(String texto) {
        return Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** Cuenta los emojis como los ve una persona: los de pictogramas, y un par con modificador es uno. */
    static int contarEmojis(String texto) {
        int emojis = 0;
        for (int i = 0; i < texto.length(); ) {
            int punto = texto.codePointAt(i);
            i += Character.charCount(punto);
            if (esEmoji(punto)) {
                emojis++;
            }
        }
        return emojis;
    }

    private static boolean esEmoji(int punto) {
        return (punto >= 0x1F300 && punto <= 0x1FAFF) || (punto >= 0x2600 && punto <= 0x27BF) || (punto >= 0x1F000 && punto <= 0x1F2FF);
    }
}
