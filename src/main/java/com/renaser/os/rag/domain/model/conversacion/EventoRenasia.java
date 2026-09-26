package com.renaser.os.rag.domain.model.conversacion;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Un evento del streaming de respuesta de Renasia. Reemplaza al {@code Flux<String>} que
 * usaba antes {@code ChatIAPort}: una cadena suelta no puede distinguir "esto es texto
 * para mostrar" de "esto son las lecciones citadas" ni de "la respuesta ya termino" — las
 * tres cosas viajaban mezcladas y el cliente (o el propio {@code ConversacionRenasiaService})
 * tenia que adivinar por convencion.
 *
 * <p><b>Por que {@code sealed}.</b> El compilador obliga a cubrir todos los casos en cada
 * {@code switch} (ver {@code ConversacionRenasiaService} y {@code EventoRenasiaSseMapper}).
 * El dia que se agregue un cuarto tipo — la herramienta pensada hoy es "el modelo invoco una
 * herramienta", para cuando Renasia pueda consultar datos en vivo del aprendiz — agregar el
 * {@code record} y declararlo en el {@code permits} implicito (o explicito) rompe la
 * compilacion en cada {@code switch} no exhaustivo en vez de fallar en silencio en produccion.
 * Es lo que permite crecer el contrato sin romper a quien ya lo consume.
 *
 * <p><b>Contrato de la SSE (ver {@code RenasiaController}, docs/MODULO_RAG.md §4.bis):</b> cada
 * variante mapea 1:1 a una de las formas fijas de {@code data:} que expone
 * {@code POST /api/v1/renasia/mensajes}. {@link Texto} puede repetirse N veces; {@link Propuesta}
 * puede repetirse N veces y siempre llega despues de un {@link Texto} con su mismo resumen;
 * {@link Fuentes} aparece a lo sumo una vez, antes de {@link Fin}; {@link Fin} siempre es el
 * ultimo evento, incluso si el streaming termino en error (esa garantia la sostiene el adaptador
 * HTTP, no este tipo — ver el manejo de errores de {@code RenasiaController}).
 *
 * <p><b>Un {@code tipo} nuevo no llega a las apps viejas.</b> La app no se actualiza por aire y
 * un {@code tipo} que no conoce lo ignora en silencio. Por eso toda variante nueva que la persona
 * TENGA que ver viaja ademas como {@link Texto} (ver {@link Propuesta}).
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

    /**
     * Fase 2, D-153: una accion de escritura que el modelo PROPUSO y que todavia no se ejecuto. La
     * app dibuja [Confirmar] [Cancelar]; confirmar llama a
     * {@code POST /api/v1/renasia/propuestas/{id}/confirmar}. El texto del chat nunca confirma.
     *
     * <p>No la arma el modelo: {@code ConversacionRenasiaService} la recoge al terminar el turno,
     * de las propuestas que se guardaron durante el turno. Siempre va precedida de un {@link Texto}
     * con el mismo {@code resumen}, para que una app anterior a los botones —que ignora este
     * {@code tipo}— al menos muestre que se propuso.
     *
     * @param venceEn desde cuando ya no se puede confirmar; la app puede ocultar los botones
     */
    record Propuesta(UUID id, String resumen, Instant venceEn) implements EventoRenasia {

        public Propuesta {
            Objects.requireNonNull(id, "id no puede ser null");
            Objects.requireNonNull(resumen, "resumen no puede ser null");
            Objects.requireNonNull(venceEn, "venceEn no puede ser null");
            if (resumen.isBlank()) {
                throw new IllegalArgumentException("Una propuesta sin resumen no le dice a la persona que confirma");
            }
        }
    }

    /**
     * D-171: la tarjeta de la camara. El acompanante no puede recibir fotos, asi que cuando la
     * persona le pide registrar un habito que exige evidencia, le deja a la app esta tarjeta: la app
     * abre la camara, sube la foto como evidencia FOTO de {@code registroId} y completa el habito
     * (en los rituales, antes pregunta "¿Que sentiste?" y lo guarda como respuesta; D-172), todo con
     * los endpoints de siempre.
     *
     * <p>A diferencia de {@link Propuesta}, no hay nada que confirmar en el servidor: no se guarda
     * en {@code propuestas_acompanante}. La junta {@code ConversacionRenasiaService} al terminar el
     * turno, de lo que pidio la herramienta, y siempre va precedida de un {@link Texto} para la app
     * que no conoce este {@code tipo}.
     *
     * @param titulo  el nombre del habito como lo ve la persona
     * @param venceEn     el fin del dia local de la persona: despues ese registro ya no es el de hoy
     * @param conPregunta si despues de la foto la app pregunta "¿Que sentiste?": solo en los rituales
     *                    (D-172, decision del dueno). En los demas, foto y listo
     */
    record Evidencia(UUID registroId, String titulo, Instant venceEn, boolean conPregunta)
            implements EventoRenasia {

        public Evidencia {
            Objects.requireNonNull(registroId, "registroId no puede ser null");
            Objects.requireNonNull(titulo, "titulo no puede ser null");
            Objects.requireNonNull(venceEn, "venceEn no puede ser null");
        }
    }

    /**
     * D-100: el modelo no pudo responder. Antes esto se tragaba en silencio y el cliente recibia
     * un {@code Fin} pelado — tres preguntas del aprendiz y ninguna respuesta, sin saber por que.
     * {@code mensaje} es apto para mostrar (nunca lleva la traza ni datos personales).
     */
    record Error(String mensaje) implements EventoRenasia {
    }

    /** Marca el final del streaming. Siempre es el ultimo evento de la secuencia. */
    record Fin() implements EventoRenasia {
    }
}
