package com.renaser.os.rag.infrastructure.adapter.in.rest.conversacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;

/**
 * Traduce cada {@link EventoRenasia} a la línea {@code data:} exacta del contrato SSE fijo
 * de {@code POST /api/v1/renasia/mensajes} (docs/MODULO_RAG.md §4.bis):
 *
 * <pre>
 * data: {"tipo":"texto","valor":"fragmento de la respuesta"}
 * data: {"tipo":"fuentes","lecciones":["leccion-id-1","leccion-id-2"]}
 * data: {"tipo":"fin"}
 * </pre>
 *
 * <p><b>Por qué un {@link ObjectMapper} propio, sin depender del conversor HTTP por
 * defecto de Spring.</b> Mismo motivo que documenta {@code PgVectorNativoAdapter} (E-33,
 * docs/BITACORA_ERRORES.md): Spring Boot 4.1 autoconfigura el {@code ObjectMapper} de
 * Jackson 3 ({@code tools.jackson.databind}), no el clásico de {@code com.fasterxml} que usa
 * esta clase. Construir el JSON a mano, con un mapper que esta clase controla por completo,
 * es lo que garantiza que estas tres formas no cambien silenciosamente porque alguien
 * reconfiguró el {@code ObjectMapper} global de la app (indentación, naming strategy,
 * inclusión de nulos, etc.) — el contrato SSE es de la app móvil, no negociable por config.
 */
final class EventoRenasiaSseMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EventoRenasiaSseMapper() {
    }

    /** Devuelve el JSON de una sola línea que va después de {@code data:} para este evento. */
    static String aJson(EventoRenasia evento) {
        ObjectNode nodo = OBJECT_MAPPER.createObjectNode();
        switch (evento) {
            case EventoRenasia.Texto texto -> {
                nodo.put("tipo", "texto");
                nodo.put("valor", texto.fragmento());
            }
            case EventoRenasia.Fuentes fuentes -> {
                nodo.put("tipo", "fuentes");
                var lecciones = nodo.putArray("lecciones");
                fuentes.leccionIds().forEach(lecciones::add);
            }
            case EventoRenasia.Error error -> {
                nodo.put("tipo", "error");
                nodo.put("valor", error.mensaje());
            }
            case EventoRenasia.Fin ignorado -> nodo.put("tipo", "fin");
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(nodo);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar un EventoRenasia a JSON", e);
        }
    }
}
