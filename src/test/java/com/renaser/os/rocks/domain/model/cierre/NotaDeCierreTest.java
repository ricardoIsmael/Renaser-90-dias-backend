package com.renaser.os.rocks.domain.model.cierre;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La nota opcional del cierre (spec 017): una sola regla para la de una acción y la del día. */
class NotaDeCierreTest {

    @Test
    @DisplayName("sin nota o en blanco no se guarda nada: null, nunca una cadena vacía (el CHECK de V121 no la deja)")
    void sinNotaEsNull() {
        assertThat(NotaDeCierre.limpiar(null)).isNull();
        assertThat(NotaDeCierre.limpiar("")).isNull();
        assertThat(NotaDeCierre.limpiar("   \n\t ")).isNull();
    }

    @Test
    @DisplayName("se recorta")
    void seRecorta() {
        assertThat(NotaDeCierre.limpiar("  Se me fue el día  ")).isEqualTo("Se me fue el día");
    }

    @Test
    @DisplayName("500 caracteres entran; 501 no")
    void elTopeEsDe500() {
        assertThat(NotaDeCierre.limpiar("a".repeat(500))).hasSize(500);
        assertThatThrownBy(() -> NotaDeCierre.limpiar("a".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("500").hasMessageContaining("501");
    }

    @Test
    @DisplayName("se cuenta como una persona: un emoji es un caracter, no dos")
    void unEmojiEsUno() {
        assertThat(NotaDeCierre.limpiar("😀".repeat(500))).isNotNull();
        assertThatThrownBy(() -> NotaDeCierre.limpiar("😀".repeat(501))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la respuesta de una accion normaliza la nota con la misma regla")
    void laRespuestaUsaLaMismaRegla() {
        RespuestaDeCierre respuesta = new RespuestaDeCierre(MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.OTRA_HORA, "  ");
        assertThat(respuesta.nota()).isNull();
    }

    @Test
    @DisplayName("un motivo o un plan desconocido es un error de quien llama, no un valor por defecto")
    void valoresDesconocidos() {
        assertThat(MotivoDeAccionPendiente.desde("SIN_ENERGIA")).isEqualTo(MotivoDeAccionPendiente.SIN_ENERGIA);
        assertThat(PlanDeManana.desde("DEJARLA")).isEqualTo(PlanDeManana.DEJARLA);
        assertThatThrownBy(() -> MotivoDeAccionPendiente.desde("OTRO")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MotivoDeAccionPendiente.desde(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlanDeManana.desde("mañana")).isInstanceOf(IllegalArgumentException.class);
    }
}
