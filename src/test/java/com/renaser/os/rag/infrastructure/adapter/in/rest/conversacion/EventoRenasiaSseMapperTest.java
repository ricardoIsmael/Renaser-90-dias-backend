package com.renaser.os.rag.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.rag.domain.model.conversacion.EventoRenasia;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El contrato SSE de {@code POST /api/v1/renasia/mensajes} (docs/MODULO_RAG.md §4.bis) es
 * fijo: estas tres formas de JSON, exactas, nada más ni distinto orden de campos.
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
}
