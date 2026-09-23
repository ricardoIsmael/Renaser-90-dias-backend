package com.renaser.os.rag.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El contrato SSE de {@code POST /api/v1/renasia/mensajes} (docs/MODULO_RAG.md §4.bis) es
 * fijo: estas formas de JSON, exactas, nada más ni distinto orden de campos.
 */
class EventoRenasiaSseMapperTest {

    @Test
    void textoSerializaATipoTextoConValor() {
        String json = EventoRenasiaSseMapper.aJson(new EventoRenasia.Texto("fragmento de la respuesta"));

        assertThat(json).isEqualTo("{\"tipo\":\"texto\",\"valor\":\"fragmento de la respuesta\"}");
    }

    @Test
    void fuentesSerializaATipoFuentesConLaListaDeLecciones() {
        String json = EventoRenasiaSseMapper.aJson(
                new EventoRenasia.Fuentes(List.of("leccion-id-1", "leccion-id-2")));

        assertThat(json).isEqualTo("{\"tipo\":\"fuentes\",\"lecciones\":[\"leccion-id-1\",\"leccion-id-2\"]}");
    }

    @Test
    void finSerializaSoloATipoFin() {
        String json = EventoRenasiaSseMapper.aJson(new EventoRenasia.Fin());

        assertThat(json).isEqualTo("{\"tipo\":\"fin\"}");
    }

    @Test
    void textoEscapaComillasYCaracteresEspecialesEnElValor() {
        String json = EventoRenasiaSseMapper.aJson(new EventoRenasia.Texto("dijo \"hola\" y salto de linea\n"));

        assertThat(json).isEqualTo("{\"tipo\":\"texto\",\"valor\":\"dijo \\\"hola\\\" y salto de linea\\n\"}");
    }

    @Test
    void errorSeSerializaConTipoYValor() {
        String json = EventoRenasiaSseMapper.aJson(new EventoRenasia.Error("no pude"));
        org.assertj.core.api.Assertions.assertThat(json).contains("\"tipo\":\"error\"").contains("no pude");
    }

    /**
     * Fase 2, D-153: la forma que la app nueva lee para dibujar [Confirmar] [Cancelar]. El orden
     * de campos y el {@code venceEn} como texto ISO-8601 en UTC son parte del contrato.
     */
    @Test
    void propuestaSerializaConIdResumenYVencimientoIso() {
        String json = EventoRenasiaSseMapper.aJson(new EventoRenasia.Propuesta(
                UUID.fromString("22222222-2222-2222-2222-222222222222"), "Meditar: de 06:00 a 07:00",
                Instant.parse("2026-09-23T15:10:00Z")));

        assertThat(json).isEqualTo("{\"tipo\":\"propuesta\",\"id\":\"22222222-2222-2222-2222-222222222222\","
                + "\"resumen\":\"Meditar: de 06:00 a 07:00\",\"venceEn\":\"2026-09-23T15:10:00Z\"}");
    }
}
