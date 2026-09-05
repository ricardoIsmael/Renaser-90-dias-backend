package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.rag.application.ports.in.herramienta.EjecutarHerramientaAgenteUseCase;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La traduccion entre una herramienta del dominio y el formato que entiende Spring AI.
 *
 * <p>Estas pruebas existen por un defecto concreto: las herramientas estaban definidas y probadas,
 * pero el adaptador de Gemini no las declaraba, asi que el modelo respondia "no tengo acceso a tu
 * cuenta" cuando le pedian los habitos del dia. Lo que se verifica aca es justamente el eslabon
 * que faltaba.
 */
class HerramientaToolCallbackTest {

    private static final UserId ACTOR = UserId.of(UUID.randomUUID());
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Recuerda con que se lo llamo, para poder afirmar sobre la invocacion y no solo el resultado. */
    private static final class EjecutorEspia implements EjecutarHerramientaAgenteUseCase {
        private final AtomicReference<UserId> actorRecibido = new AtomicReference<>();
        private final AtomicReference<InvocacionHerramienta> invocacionRecibida = new AtomicReference<>();
        private final ResultadoHerramienta respuesta;

        EjecutorEspia(ResultadoHerramienta respuesta) {
            this.respuesta = respuesta;
        }

        @Override
        public List<DefinicionHerramienta> disponibles(AgenteConversacional agente) {
            return List.of();
        }

        @Override
        public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
            actorRecibido.set(actorId);
            invocacionRecibida.set(invocacion);
            return respuesta;
        }
    }

    private static DefinicionHerramienta sinParametros() {
        return DefinicionHerramienta.sinParametros("consultar_habitos_del_dia",
                "Los habitos que le tocan hoy a la persona.");
    }

    private static DefinicionHerramienta conParametro() {
        return new DefinicionHerramienta("marcar_habito_completado", "Marca un habito como hecho.",
                List.of(ParametroHerramienta.obligatorio("habitoId", TipoParametroHerramienta.IDENTIFICADOR,
                        "El identificador del habito a marcar.")));
    }

    @Test
    void declaraNombreYDescripcionParaQueElModeloSepaCuandoLlamarla() {
        var callback = new HerramientaToolCallback(sinParametros(),
                new EjecutorEspia(ResultadoHerramienta.exito("ok")), ACTOR, JSON);

        var definicion = callback.getToolDefinition();

        assertThat(definicion.name()).isEqualTo("consultar_habitos_del_dia");
        assertThat(definicion.description()).isEqualTo("Los habitos que le tocan hoy a la persona.");
    }

    @Test
    void unaHerramientaSinParametrosIgualDeclaraUnObjetoJsonValido() {
        var callback = new HerramientaToolCallback(sinParametros(),
                new EjecutorEspia(ResultadoHerramienta.exito("ok")), ACTOR, JSON);

        // Un esquema vacio o nulo lo rechaza el SDK: tiene que ser un objeto, aunque no tenga campos.
        assertThat(callback.getToolDefinition().inputSchema()).contains("\"type\":\"object\"");
    }

    @Test
    void elParametroObligatorioViajaComoRequeridoEnElEsquema() {
        var callback = new HerramientaToolCallback(conParametro(),
                new EjecutorEspia(ResultadoHerramienta.exito("ok")), ACTOR, JSON);

        String esquema = callback.getToolDefinition().inputSchema();

        assertThat(esquema).contains("habitoId");
        assertThat(esquema).contains("El identificador del habito a marcar.");
        assertThat(esquema).contains("required");
    }

    @Test
    void elActorLoPoneLaConversacionYNoElModelo() {
        var espia = new EjecutorEspia(ResultadoHerramienta.exito("tu primer habito de hoy es Agua"));
        var callback = new HerramientaToolCallback(conParametro(), espia, ACTOR, JSON);

        // El modelo intenta operar sobre otra persona. No tiene por donde: el actor no sale de aca.
        callback.call("{\"habitoId\":\"abc\",\"usuarioId\":\"otra-persona\"}");

        assertThat(espia.actorRecibido.get()).isEqualTo(ACTOR);
    }

    @Test
    void losArgumentosDelModeloLleganAlCasoDeUso() {
        var espia = new EjecutorEspia(ResultadoHerramienta.exito("hecho"));
        var callback = new HerramientaToolCallback(conParametro(), espia, ACTOR, JSON);

        String salida = callback.call("{\"habitoId\":\"habito-7\"}");

        assertThat(espia.invocacionRecibida.get().nombre()).isEqualTo("marcar_habito_completado");
        assertThat(espia.invocacionRecibida.get().argumento("habitoId")).isEqualTo("habito-7");
        assertThat(salida).contains("\"resultado\":\"hecho\"");
    }

    @Test
    void unFalloSeLeDevuelveAlModeloComoTextoEnVezDeLanzar() {
        var espia = new EjecutorEspia(ResultadoHerramienta.fallo("Ese habito ya vencio, no se puede marcar."));
        var callback = new HerramientaToolCallback(conParametro(), espia, ACTOR, JSON);

        // Si esto lanzara, el asistente se quedaria mudo a mitad de la frase en vez de explicarlo.
        assertThat(callback.call("{\"habitoId\":\"habito-7\"}"))
                .contains("\"ok\":false")
                .contains("Ese habito ya vencio, no se puede marcar.");
    }

    @Test
    void elResultadoVuelveComoObjetoJsonPorqueGeminiLoParsea() throws Exception {
        var espia = new EjecutorEspia(ResultadoHerramienta.exito(
                "id=93ef82a1 | ULTIMA COMIDA DEL DIA | estado=PENDIENTE\nid=a4435586 | DIA SIN CELULAR"));
        var callback = new HerramientaToolCallback(sinParametros(), espia, ACTOR, JSON);

        // El adaptador de Google hace parseJsonToMap() sobre esto antes de mandarlo. Devolver
        // texto plano mata la conversacion entera con "Failed to parse JSON", DESPUES de que la
        // herramienta ya se ejecuto bien. Por eso el contrato es objeto JSON, y por eso se fija aca.
        var comoMapa = JSON.readValue(callback.call(null), java.util.Map.class);

        assertThat(comoMapa).containsEntry("ok", true);
        assertThat(comoMapa.get("resultado").toString()).contains("ULTIMA COMIDA DEL DIA");
    }

    @Test
    void argumentosIlegiblesNoTumbanLaConversacion() {
        var espia = new EjecutorEspia(ResultadoHerramienta.fallo("Falta el identificador del habito."));
        var callback = new HerramientaToolCallback(conParametro(), espia, ACTOR, JSON);

        // Un modelo manda JSON roto con total naturalidad. Se ejecuta igual, sin argumentos, y es
        // el caso de uso el que decide que responder — no una excepcion a mitad del stream.
        String salida = callback.call("{esto no es json");

        assertThat(espia.invocacionRecibida.get().argumentos()).isEmpty();
        assertThat(salida).contains("Falta el identificador del habito.");
    }

    @Test
    void sinArgumentosEsUnaInvocacionValida() {
        var espia = new EjecutorEspia(ResultadoHerramienta.exito("Agua, Meditacion"));
        var callback = new HerramientaToolCallback(sinParametros(), espia, ACTOR, JSON);

        assertThat(callback.call(null)).contains("Agua, Meditacion");
        assertThat(espia.invocacionRecibida.get().argumentos()).isEmpty();
    }
}
