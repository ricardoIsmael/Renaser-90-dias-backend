package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.habits.api.RespuestasDelRadarFinder.RespuestaDelRadar;
import com.renaser.os.rag.application.services.analisis.ArmadoDelPedido.PedidoArmado;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.insumos;
import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.respuesta;
import static com.renaser.os.rag.application.services.analisis.CifrasDePrueba.semanaDeDoce;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que viaja a la IA y lo que se guarda (spec 031, FR-002, FR-006 y FR-023): una sola fuente de cifras, y nada que identifique a la
 * persona más allá de lo que escribió.
 */
class ArmadoDelPedidoTest {

    private static final Instant MANANA = Instant.parse("2026-10-20T14:00:00Z");

    private static List<RespuestaDelRadar> tresRespuestas() {
        return List.of(respuesta(MANANA, 8, "el correo"), respuesta(MANANA.plusSeconds(3600), 4, "el celular"),
                respuesta(MANANA.plusSeconds(7200), 5, "el celular"));
    }

    @Test
    @DisplayName("el pedido lleva el periodo, las cifras calculadas, la muestra del Código Renaser y lo escrito al cerrar el día")
    void elPedido() {
        PedidoArmado armado = ArmadoDelPedido.armar(TipoDePeriodo.SEMANA, insumos(semanaDeDoce(), tresRespuestas()));

        JsonNode datos = JsonDelAnalisis.arbol(armado.pedido().datosEnJson());
        assertThat(armado.pedido().tipo()).isEqualTo(TipoDePeriodo.SEMANA);
        assertThat(datos.path("periodo").asString()).isEqualTo("SEMANA");
        assertThat(datos.path("numero").asInt()).isEqualTo(10);
        assertThat(datos.path("cifras").path("planificadas").asInt()).isEqualTo(12);
        assertThat(datos.path("cifras").path("verdes").path("porcentajeDelImpacto").asInt()).isEqualTo(55);
        assertThat(datos.path("cifras").path("lasQueSeFueron").get(0).path("titulo").asString()).startsWith("Llamar al cliente");
        assertThat(datos.path("codigoRenaser").path("respuestas").asInt()).isEqualTo(3);
        assertThat(datos.path("escritoEnElCodigoRenaser")).hasSize(3);
        assertThat(datos.path("escritoAlCerrarElDia").get(0).asString()).isEqualTo("Llegué agotada");
    }

    @Test
    @DisplayName("FR-013: sin suficientes respuestas del Código Renaser no viaja nada de él y el análisis lo dice")
    void sinCodigoRenaser() {
        PedidoArmado armado = ArmadoDelPedido.armar(TipoDePeriodo.SEMANA, insumos(semanaDeDoce(), List.of(respuesta(MANANA, 8, "x"))));

        JsonNode datos = JsonDelAnalisis.arbol(armado.pedido().datosEnJson());
        assertThat(datos.path("codigoRenaser").isNull()).isTrue();
        assertThat(datos.path("escritoEnElCodigoRenaser")).isEmpty();
        assertThat(armado.foto().codigoRenaser()).isNull();
    }

    @Test
    @DisplayName("los números que el texto puede nombrar salen de la foto, de los títulos de las acciones y del propio periodo; no más")
    void numerosPermitidos() {
        PedidoArmado armado = ArmadoDelPedido.armar(TipoDePeriodo.SEMANA, insumos(semanaDeDoce(), tresRespuestas()));

        assertThat(armado.numerosPermitidos()).contains("12", "9", "75", "55", "38", "47", "66", "30", "10", "19", "25", "5.7");
        assertThat(armado.numerosPermitidos()).doesNotContain("13", "100", "7");
    }

    @Test
    @DisplayName("FR-023: no viaja ningún dato de contacto ni de identidad: ni correo, ni teléfono, ni documento, ni id de la persona")
    void sinIdentidad() {
        String datos = ArmadoDelPedido.armar(TipoDePeriodo.SEMANA, insumos(semanaDeDoce(), tresRespuestas())).pedido().datosEnJson();

        assertThat(datos).doesNotContain("@").doesNotContainIgnoringCase("correo electr").doesNotContainIgnoringCase("participante")
                .doesNotContainIgnoringCase("documento").doesNotContainIgnoringCase("telefono").doesNotContainIgnoringCase("userId");
    }

    @Test
    @DisplayName("FR-006: la foto que se guarda es la misma que se leyó: se vuelve a leer igual")
    void laFotoSeVuelveALeer() {
        PedidoArmado armado = ArmadoDelPedido.armar(TipoDePeriodo.SEMANA, insumos(semanaDeDoce(), tresRespuestas()));

        SnapshotDelAnalisis leida = JsonDelAnalisis.leer(JsonDelAnalisis.escribir(armado.foto()), SnapshotDelAnalisis.class);

        assertThat(leida).isEqualTo(armado.foto());
        assertThat(leida.cifras().desde()).isEqualTo(semanaDeDoce().desde());
    }
}
