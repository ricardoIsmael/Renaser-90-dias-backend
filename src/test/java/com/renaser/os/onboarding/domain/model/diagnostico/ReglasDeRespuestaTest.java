package com.renaser.os.onboarding.domain.model.diagnostico;

import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReglasDeRespuestaTest {

    private static RespuestaEntrante texto(int id, String t) {
        return new RespuestaEntrante(id, t, null, null, null, null);
    }

    @Test
    @DisplayName("una respuesta vacía vuelve vacía: quien guarda entiende «borrar»")
    void vacia() {
        PreguntaDelDiagnostico p = Fichas.pregunta(1, TipoPreguntaOnboarding.TEXTO, true);
        assertThat(ReglasDeRespuesta.validar(p, texto(1, "  ")).estaVacia()).isTrue();
    }

    @Test
    @DisplayName("un objetivo de más de 500 caracteres se rechaza, no se recorta")
    void objetivoLargo() {
        PreguntaDelDiagnostico p = Fichas.alimenta(1, DestinoDeRespuesta.OBJETIVO_CUERPO, TipoPreguntaOnboarding.AREA_TEXTO, List.of());
        assertThat(ReglasDeRespuesta.validar(p, texto(1, "a".repeat(500))).valorTexto()).hasSize(500);
        assertThatThrownBy(() -> ReglasDeRespuesta.validar(p, texto(1, "a".repeat(501)))).hasMessageContaining("500");
    }

    @Test
    @DisplayName("la opción única tiene que ser una de las opciones")
    void opcionUnica() {
        PreguntaDelDiagnostico p = Fichas.pregunta(1, TipoPreguntaOnboarding.SELECCION_UNICA, true);
        assertThat(ReglasDeRespuesta.validar(p, texto(1, "Sí.")).valorTexto()).isEqualTo("Sí.");
        assertThatThrownBy(() -> ReglasDeRespuesta.validar(p, texto(1, "Quizás"))).hasMessageContaining("no existe");
    }

    @Test
    @DisplayName("las casillas respetan el máximo y no repiten opciones")
    void casillas() {
        PreguntaDelDiagnostico p = new PreguntaDelDiagnostico(1, "diag_p1", 1, 1, "¿Qué suele ocurrir?", null, TipoPreguntaOnboarding.SELECCION_MULTIPLE, false, 2,
                Fichas.opcionesPara(TipoPreguntaOnboarding.SELECCION_MULTIPLE), null, null, null, Set.of(), null, null);
        RespuestaEntrante dos = new RespuestaEntrante(1, null, null, null, null, "[\"Sí.\",\"No.\"]");
        assertThat(ReglasDeRespuesta.validar(p, dos).valorJson()).isEqualTo("[\"Sí.\",\"No.\"]");
        RespuestaEntrante tres = new RespuestaEntrante(1, null, null, null, null, "[\"Sí.\",\"No.\",\"Tal vez.\"]");
        assertThatThrownBy(() -> ReglasDeRespuesta.validar(p, tres)).hasMessageContaining("Elige como máximo 2");
        RespuestaEntrante repetida = new RespuestaEntrante(1, null, null, null, null, "[\"Sí.\",\"Sí.\"]");
        assertThatThrownBy(() -> ReglasDeRespuesta.validar(p, repetida)).hasMessageContaining("dos veces");
    }

    @Test
    @DisplayName("la cuadrícula acepta filas parciales al guardar, pero solo cuenta como contestada con todas")
    void cuadricula() {
        PreguntaDelDiagnostico p = Fichas.pregunta(1, TipoPreguntaOnboarding.CUADRICULA, true);
        RespuestaEntrante parcial = new RespuestaEntrante(1, null, null, null, null, "{\"Fila A\":4}");
        assertThat(ReglasDeRespuesta.validar(p, parcial).valorJson()).isEqualTo("{\"Fila A\":4}");
        assertThat(ReglasDeRespuesta.estaContestada(p, Fichas.cuadricula(1, "{\"Fila A\":4}"))).isFalse();
        assertThat(ReglasDeRespuesta.estaContestada(p, Fichas.cuadricula(1, "{\"Fila A\":4,\"Fila B\":2}"))).isTrue();
        RespuestaEntrante fuera = new RespuestaEntrante(1, null, null, null, null, "{\"Fila A\":9}");
        assertThatThrownBy(() -> ReglasDeRespuesta.validar(p, fuera)).hasMessageContaining("1 a 5");
        RespuestaEntrante otra = new RespuestaEntrante(1, null, null, null, null, "{\"Fila Z\":3}");
        assertThatThrownBy(() -> ReglasDeRespuesta.validar(p, otra)).hasMessageContaining("no existe");
    }

    @Test
    @DisplayName("el número, la fecha y el consentimiento")
    void numeroFechaYConsentimiento() {
        PreguntaDelDiagnostico numero = Fichas.pregunta(1, TipoPreguntaOnboarding.NUMERO, true);
        assertThat(ReglasDeRespuesta.validar(numero, new RespuestaEntrante(1, null, new BigDecimal("34"), null, null, null)).valorNumero())
                .isEqualByComparingTo("34");
        PreguntaDelDiagnostico fecha = Fichas.pregunta(2, TipoPreguntaOnboarding.FECHA, true);
        assertThat(ReglasDeRespuesta.validar(fecha, texto(2, "25/12/1990")).valorTexto()).isEqualTo("25/12/1990");
        assertThatThrownBy(() -> ReglasDeRespuesta.validar(fecha, texto(2, "31/02/1990"))).hasMessageContaining("día real");
        PreguntaDelDiagnostico consentimiento = Fichas.pregunta(3, TipoPreguntaOnboarding.CASILLA, true);
        assertThat(ReglasDeRespuesta.validar(consentimiento, new RespuestaEntrante(3, null, null, true, null, null)).valorBooleano()).isTrue();
        assertThat(ReglasDeRespuesta.validar(consentimiento, new RespuestaEntrante(3, null, null, false, null, null)).estaVacia()).isTrue();
    }

    @Test
    @DisplayName("un valor en el slot equivocado se rechaza")
    void slotEquivocado() {
        PreguntaDelDiagnostico p = Fichas.pregunta(1, TipoPreguntaOnboarding.TEXTO, true);
        assertThatThrownBy(() -> ReglasDeRespuesta.validar(p, new RespuestaEntrante(1, null, new BigDecimal("3"), null, null, null)))
                .hasMessageContaining("no corresponde al tipo");
    }
}
