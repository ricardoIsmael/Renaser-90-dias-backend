package com.renaser.os.rag.domain.model.intencion;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Elige, entre un conjunto CERRADO de etiquetas, la que mas se parece a un mensaje.
 *
 * <p><b>Por que conjunto cerrado.</b> El resultado solo puede ser una etiqueta que armo el codigo:
 * una intencion del catalogo o el {@code registroId} de un habito de hoy de esa persona. Un
 * modelo que genera texto puede inventar un identificador; esto no puede, por construccion. Es lo
 * que protege a {@code marcar_habito_completado} de un id alucinado.
 *
 * <p><b>Y por que ayuda con la voz.</b> Si el reconocedor transcribe "ya tome awa", la comparacion
 * no busca la palabra "agua": compara el sentido contra los tres o cuatro habitos que la persona
 * tiene hoy, y ahi hay una sola opcion sensata.
 *
 * <p>Codigo puro, sin Spring ni proveedor: recibe vectores ya calculados. Quien los calcula
 * (Gemini, un modelo en el telefono) es asunto del adaptador.
 */
public final class ComparacionSemantica {

    private ComparacionSemantica() {
    }

    /**
     * @return vacio si no hay referencias contra las cuales comparar
     */
    public static Optional<Candidato> mejorCandidato(List<Float> mensaje, List<Referencia> referencias) {
        List<Map.Entry<String, Double>> ranking = mejorSimilitudPorEtiqueta(mensaje, referencias).entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .toList();
        if (ranking.isEmpty()) {
            return Optional.empty();
        }
        double primera = ranking.get(0).getValue();
        double margen = ranking.size() > 1 ? primera - ranking.get(1).getValue() : primera;
        return Optional.of(new Candidato(ranking.get(0).getKey(), primera, margen));
    }

    /** Una etiqueta con varias frases de ejemplo vale lo que su frase mas parecida al mensaje. */
    private static Map<String, Double> mejorSimilitudPorEtiqueta(List<Float> mensaje, List<Referencia> referencias) {
        Map<String, Double> mejores = new HashMap<>();
        for (Referencia referencia : referencias) {
            mejores.merge(referencia.etiqueta(), coseno(mensaje, referencia.vector()), Math::max);
        }
        return mejores;
    }

    /**
     * Coseno entre dos vectores. Un vector nulo (todo ceros, lo que devuelve el adaptador de
     * embeddings {@code noop}) no se parece a nada: da 0 en vez de dividir por cero.
     */
    static double coseno(List<Float> a, List<Float> b) {
        if (a.size() != b.size()) {
            throw new IllegalArgumentException(
                    "Los vectores tienen dimensiones distintas: " + a.size() + " y " + b.size());
        }
        double producto = 0;
        double normaA = 0;
        double normaB = 0;
        for (int i = 0; i < a.size(); i++) {
            producto += a.get(i) * b.get(i);
            normaA += a.get(i) * a.get(i);
            normaB += b.get(i) * b.get(i);
        }
        return normaA == 0 || normaB == 0 ? 0 : producto / (Math.sqrt(normaA) * Math.sqrt(normaB));
    }
}
