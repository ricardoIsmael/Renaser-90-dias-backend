package com.renaser.os.rag.domain.model.conversacion;

import java.util.List;
import java.util.Objects;

/**
 * Un evento del streaming de respuesta de Renasia. Reemplaza al {@code Flux<String>} que
 * usaba antes {@code ChatIAPort}: una cadena suelta no puede distinguir "esto es texto
 * para mostrar" de "esto son las lecciones citadas" ni de "la respuesta ya termino" — las
 * tres cosas viajaban mezcladas y el cliente (o el propio {@code ConversacionRenasiaService})
 * tenia que adivinar por convencion.
 *
 * <p><b>Por que {@code sealed}.</b> El compilador obliga a cubrir los tres casos en cada
 * {@code switch} (ver {@code ConversacionRenasiaService} y {@code EventoRenasiaSseMapper}).
 * El dia que se agregue un cuarto tipo — la herramienta pensada hoy es "el modelo invoco una
 * herramienta", para cuando Renasia pueda consultar datos en vivo del aprendiz — agregar el
 * {@code record} y declararlo en el {@code permits} implicito (o explicito) rompe la
 * compilacion en cada {@code switch} no exhaustivo en vez de fallar en silencio en produccion.
 * Es lo que permite crecer el contrato sin romper a quien ya lo consume.
 *
 * <p><b>Contrato de la SSE (ver {@code RenasiaController}, docs/MODULO_RAG.md §4.bis):</b> cada
 * variante mapea 1:1 a una de las tres formas fijas de {@code data:} que expone
 * {@code POST /api/v1/renasia/mensajes}. {@link Texto} puede repetirse N veces; {@link Fuentes}
 * aparece a lo sumo una vez, antes de {@link Fin}; {@link Fin} siempre es el ultimo evento,
 * incluso si el streaming termino en error (esa garantia la sostiene el adaptador HTTP, no este
 * tipo — ver el manejo de errores de {@code RenasiaController}).
 */
public sealed interface EventoRenasia {

    /** Un fragmento de texto de la respuesta, en el orden en que el modelo lo genero. */
    record Texto(String fragmento) implements EventoRenasia {

        public Texto {
            Objects.requireNonNull(fragmento, "fragmento no puede ser null");
        }
    }

    /**
     * Las lecciones de la base de conocimiento que sirvieron de contexto para la respuesta.
     * No la arma el modelo: la arma {@code ConversacionRenasiaService} a partir de lo que
     * {@code VectorStorePort} ya recupero antes de preguntarle a la IA (ver
     * {@code ChatIAPort}, que solo conoce texto de contexto, no que leccion lo origino).
     */
    record Fuentes(List<String> leccionIds) implements EventoRenasia {

        public Fuentes {
            Objects.requireNonNull(leccionIds, "leccionIds no puede ser null");
            if (leccionIds.isEmpty()) {
                throw new IllegalArgumentException("Fuentes no debe emitirse con una lista vacia de lecciones");
            }
            leccionIds = List.copyOf(leccionIds);
        }
    }

    /** Marca el final del streaming. Siempre es el ultimo evento de la secuencia. */
    record Fin() implements EventoRenasia {
    }
}
