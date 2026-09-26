package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;

import java.util.List;
import java.util.Optional;

/**
 * Las dos preguntas que se contestan despues de escuchar la Pastilla Renacer o la Audioterapia
 * semanal (D-97), y el texto que se entrega con las respuestas (D-171).
 *
 * <p><b>Tienen que ser identicas a las de la app</b> ({@code features/spirit/data/preguntasPastilla.ts},
 * {@code PREGUNTAS_FIJAS}), letra por letra, y el formato el mismo que arma su modal:
 * <pre>
 * preguntas.map((p, i) =&gt; `${p}\n${respuesta}`).join('\n\n')
 * </pre>
 * Asi lo que entrega el acompanante y lo que entrega la pantalla quedan guardados igual, y el mentor
 * lee lo mismo venga de donde venga. El texto lo arma el codigo y no el modelo para que el formato
 * no pueda derivar: el modelo solo pasa las palabras de la persona, una por pregunta.
 */
final class PreguntasDelAudio {

    static final String QUE_SENTISTE = "¿Qué sentiste después de escuchar este audio?";
    static final String QUE_TE_LLEVAS = "¿Qué te llevas de este audio para tu día de hoy?";

    static final String ARGUMENTO_QUE_SENTISTE = "que_sentiste";
    static final String ARGUMENTO_QUE_TE_LLEVAS = "que_te_llevas";

    /** Los dos parametros, iguales en la Pastilla y en la Audioterapia. */
    static final List<ParametroHerramienta> PARAMETROS = List.of(
            ParametroHerramienta.obligatorio(ARGUMENTO_QUE_SENTISTE, TipoParametroHerramienta.TEXTO,
                    "Lo que la persona contesto a \"" + QUE_SENTISTE + "\", con SUS palabras y completo, aunque "
                            + "sea largo. No lo resumas ni lo mejores."),
            ParametroHerramienta.obligatorio(ARGUMENTO_QUE_TE_LLEVAS, TipoParametroHerramienta.TEXTO,
                    "Lo que la persona contesto a \"" + QUE_TE_LLEVAS + "\", con SUS palabras y completo, aunque "
                            + "sea largo. No lo resumas ni lo mejores."));

    /** Lo que se le dice al modelo en la descripcion de las dos herramientas. */
    static final String COMO_PREGUNTAR = "Antes, hazle a la persona estas dos preguntas, de a una y con estas "
            + "palabras: \"" + QUE_SENTISTE + "\" y \"" + QUE_TE_LLEVAS + "\". Deja que conteste todo lo que "
            + "quiera, sin cortarla. Pasa cada respuesta tal cual la dijo: nunca la inventes, la completes ni la "
            + "resumas; si falta una, preguntasela.";

    private PreguntasDelAudio() {
    }

    /** Vacio si falta alguna de las dos respuestas. */
    static Optional<String> textoDe(InvocacionHerramienta invocacion) {
        String queSentiste = limpia(invocacion.argumento(ARGUMENTO_QUE_SENTISTE));
        String queTeLlevas = limpia(invocacion.argumento(ARGUMENTO_QUE_TE_LLEVAS));
        if (queSentiste.isEmpty() || queTeLlevas.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(QUE_SENTISTE + "\n" + queSentiste + "\n\n" + QUE_TE_LLEVAS + "\n" + queTeLlevas);
    }

    private static String limpia(String respuesta) {
        return respuesta == null ? "" : respuesta.trim();
    }
}
