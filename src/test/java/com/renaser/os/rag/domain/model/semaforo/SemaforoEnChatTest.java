package com.renaser.os.rag.domain.model.semaforo;

import com.renaser.os.rag.domain.model.semaforo.SemaforoEnChat.CierreDeSemana;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** La redacción del cierre semanal en el chat, sin Spring: plantilla y datos del evento, nada más. */
class SemaforoEnChatTest {

    private static final String VERDE = "Cerraste la semana en verde, {etiqueta}: {porcentaje} %. ¡Bien hecho!";
    private static final String AMARILLO = "Cerraste la semana en amarillo, {etiqueta}: {porcentaje} %. Vamos.";
    private static final String ROJO = "Cerraste la semana en rojo, {etiqueta}: {porcentaje} %. Aquí estoy.";
    private static final String SIN_DATOS = "Tu semana cerró sin datos. ¿Planificamos juntos esta semana?";

    private static SemaforoEnChat prendido() {
        return new SemaforoEnChat(true, Map.of(ColorDeLaSemana.VERDE, VERDE, ColorDeLaSemana.AMARILLO, AMARILLO,
                ColorDeLaSemana.ROJO, ROJO, ColorDeLaSemana.SIN_DATOS, SIN_DATOS));
    }

    @Test
    @DisplayName("color, palabra y porcentaje: un entero sale sin decimales")
    void verdeConPorcentajeEntero() {
        var cierre = new CierreDeSemana(ColorDeLaSemana.VERDE, "Al día", new BigDecimal("86.0"));

        assertThat(prendido().redactar(cierre)).contains("Cerraste la semana en verde, Al día: 86 %. ¡Bien hecho!");
    }

    @Test
    @DisplayName("un porcentaje con decimal sale con punto, igual que en la tarjeta de la app")
    void amarilloConDecimal() {
        var cierre = new CierreDeSemana(ColorDeLaSemana.AMARILLO, "Requiere atención", new BigDecimal("78.3"));

        assertThat(prendido().redactar(cierre))
                .contains("Cerraste la semana en amarillo, Requiere atención: 78.3 %. Vamos.");
    }

    @Test
    @DisplayName("el cien sale como 100 y no en notacion cientifica")
    void cienPorCiento() {
        var cierre = new CierreDeSemana(ColorDeLaSemana.VERDE, "Al día", new BigDecimal("100.0"));

        assertThat(prendido().redactar(cierre)).hasValueSatisfying(texto -> assertThat(texto).contains(": 100 %"));
    }

    @Test
    @DisplayName("sin datos no dice ningun numero")
    void sinDatosSinNumero() {
        var cierre = new CierreDeSemana(ColorDeLaSemana.SIN_DATOS, "Sin datos", null);

        assertThat(prendido().redactar(cierre)).hasValueSatisfying(texto -> {
            assertThat(texto).isEqualTo(SIN_DATOS);
            assertThat(texto).doesNotContainPattern("\\d");
        });
    }

    @Test
    @DisplayName("una semana sin porcentaje usa el texto de sin datos aunque traiga otro color: nunca un numero")
    void sinPorcentajeNuncaDiceUnNumero() {
        var cierre = new CierreDeSemana(ColorDeLaSemana.ROJO, "Con problemas", null);

        assertThat(prendido().redactar(cierre)).hasValueSatisfying(texto -> {
            assertThat(texto).isEqualTo(SIN_DATOS);
            assertThat(texto).doesNotContainPattern("\\d").doesNotContain("{porcentaje}");
        });
    }

    @Test
    @DisplayName("un marcador mal escrito nunca llega a la persona: sale el texto de respaldo")
    void marcadorMalEscrito() {
        var chat = new SemaforoEnChat(true, Map.of(ColorDeLaSemana.VERDE, "Cerraste en verde, {porcentje} %."));
        var cierre = new CierreDeSemana(ColorDeLaSemana.VERDE, "Al día", new BigDecimal("86.0"));

        assertThat(chat.redactar(cierre)).contains(SemaforoEnChat.TEXTO_DE_RESPALDO);
    }

    @Test
    @DisplayName("un porcentaje pedido en la plantilla de sin datos tampoco queda a la vista")
    void porcentajeEnSinDatos() {
        var chat = new SemaforoEnChat(true, Map.of(ColorDeLaSemana.SIN_DATOS, "Cerraste en {porcentaje} %."));
        var cierre = new CierreDeSemana(ColorDeLaSemana.SIN_DATOS, "Sin datos", null);

        assertThat(chat.redactar(cierre)).hasValueSatisfying(texto -> {
            assertThat(texto).isEqualTo(SemaforoEnChat.TEXTO_DE_RESPALDO);
            assertThat(texto).doesNotContain("{").doesNotContainPattern("\\d");
        });
    }

    @Test
    @DisplayName("con el interruptor apagado no redacta nada")
    void apagadoNoRedacta() {
        var cierre = new CierreDeSemana(ColorDeLaSemana.VERDE, "Al día", new BigDecimal("86.0"));

        assertThat(SemaforoEnChat.apagado().redactar(cierre)).isEmpty();
        assertThat(new SemaforoEnChat(false, prendido().plantillas()).aplicaA(cierre)).isFalse();
    }

    @Test
    @DisplayName("una plantilla vacia apaga ese color, y solo ese")
    void plantillaVaciaApagaSuColor() {
        var soloVerde = new SemaforoEnChat(true, Map.of(ColorDeLaSemana.VERDE, VERDE, ColorDeLaSemana.ROJO, " "));

        assertThat(soloVerde.aplicaA(new CierreDeSemana(ColorDeLaSemana.ROJO, "Con problemas", BigDecimal.TEN)))
                .isFalse();
        assertThat(soloVerde.aplicaA(new CierreDeSemana(ColorDeLaSemana.VERDE, "Al día", new BigDecimal("90.0"))))
                .isTrue();
    }
}
