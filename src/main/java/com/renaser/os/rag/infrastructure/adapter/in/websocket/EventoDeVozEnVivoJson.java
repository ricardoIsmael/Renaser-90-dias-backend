package com.renaser.os.rag.infrastructure.adapter.in.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.renaser.os.rag.domain.model.conversacion.EventoDeVozEnVivo;

/**
 * Los frames de texto del WebSocket de voz en vivo, exactamente como los fija §5.ter de
 * {@code docs/arquitectura/PROPUESTA_GEMINI_LIVE.md} (D-162):
 *
 * <pre>
 * {"tipo":"listo","segundosRestantes":600}
 * {"tipo":"oido","texto":"..."}
 * {"tipo":"dicho","texto":"..."}
 * {"tipo":"interrumpido"}
 * {"tipo":"turnoCompleto"}
 * {"tipo":"propuesta","id":"&lt;uuid&gt;","resumen":"...","venceEn":"2026-09-24T15:10:00Z"}
 * {"tipo":"evidencia","registroId":"&lt;uuid&gt;","titulo":"JUGO VERDE","venceEn":"2026-09-27T05:00:00Z"}
 * {"tipo":"cuotaAgotada"}
 * {"tipo":"error","valor":"texto apto para mostrar"}
 * </pre>
 *
 * y el unico que manda la app: {@code {"tipo":"fin"}}.
 *
 * <p>{@code propuesta} y {@code evidencia} (D-171) tienen la misma forma que en el SSE del chat
 * ({@code EventoRenasiaSseMapper}).
 * Mapper propio de Jackson 2 por lo mismo que ahi (E-33): el contrato es de la app, no de la
 * configuracion global.
 */
final class EventoDeVozEnVivoJson {

    private static final ObjectMapper JSON = new ObjectMapper();

    private EventoDeVozEnVivoJson() {
    }

    static String aJson(EventoDeVozEnVivo evento) {
        ObjectNode nodo = JSON.createObjectNode();
        switch (evento) {
            case EventoDeVozEnVivo.Listo listo -> nodo.put("tipo", "listo")
                    .put("segundosRestantes", listo.segundosRestantes());
            case EventoDeVozEnVivo.Oido oido -> nodo.put("tipo", "oido").put("texto", oido.texto());
            case EventoDeVozEnVivo.Dicho dicho -> nodo.put("tipo", "dicho").put("texto", dicho.texto());
            case EventoDeVozEnVivo.Interrumpido ignorado -> nodo.put("tipo", "interrumpido");
            case EventoDeVozEnVivo.TurnoCompleto ignorado -> nodo.put("tipo", "turnoCompleto");
            case EventoDeVozEnVivo.Propuesta propuesta -> nodo.put("tipo", "propuesta")
                    .put("id", propuesta.id().toString())
                    .put("resumen", propuesta.resumen())
                    .put("venceEn", propuesta.venceEn().toString());
            case EventoDeVozEnVivo.Evidencia evidencia -> nodo.put("tipo", "evidencia")
                    .put("registroId", evidencia.registroId().toString())
                    .put("titulo", evidencia.titulo())
                    .put("venceEn", evidencia.venceEn().toString());
            case EventoDeVozEnVivo.CuotaAgotada ignorado -> nodo.put("tipo", "cuotaAgotada");
            case EventoDeVozEnVivo.Error error -> nodo.put("tipo", "error").put("valor", error.valor());
        }
        try {
            return JSON.writeValueAsString(nodo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar un evento de voz en vivo", e);
        }
    }

    /** {@code true} solo para {@code {"tipo":"fin"}}. Lo demas (o un JSON roto) se ignora. */
    static boolean esFin(String texto) {
        try {
            JsonNode nodo = JSON.readTree(texto);
            return nodo != null && "fin".equals(nodo.path("tipo").asText(null));
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
