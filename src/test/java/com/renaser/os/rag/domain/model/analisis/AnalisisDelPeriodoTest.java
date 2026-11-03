package com.renaser.os.rag.domain.model.analisis;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La máquina de estados del análisis (spec 031, FR-006 y FR-016): listo es definitivo; cada fallo suma uno; al máximo, agotado. */
class AnalisisDelPeriodoTest {

    private static final Instant T0 = Instant.parse("2026-10-26T05:30:00Z");
    private static final ReglasDelAnalisis REGLAS = ReglasDelAnalisis.POR_DEFECTO;
    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final TextoDelAnalisis TEXTO = new TextoDelAnalisis("a", "b", "c", List.of("d"));

    private static AnalisisDelPeriodo pendiente() {
        return AnalisisDelPeriodo.pendiente(AnalisisDelPeriodoId.of(UUID.randomUUID()), ANA, Formacion.ESCUELA, TipoDePeriodo.SEMANA, 10,
                LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), FixedClock.at(T0));
    }

    @Test
    @DisplayName("nace pendiente, sin intentos, y toca intentar de inmediato")
    void nace() {
        AnalisisDelPeriodo analisis = pendiente();

        assertThat(analisis.estado()).isEqualTo(EstadoDelAnalisis.PENDIENTE);
        assertThat(analisis.intentos()).isZero();
        assertThat(analisis.tocaIntentarEn(T0, REGLAS)).isTrue();
    }

    @Test
    @DisplayName("un intento bueno lo deja listo con el texto y las cifras que cita, y ya no se mueve")
    void completar() {
        AnalisisDelPeriodo analisis = pendiente();

        analisis.completar(TEXTO, "{\"cifras\":{}}", FixedClock.at(T0.plusSeconds(30)));

        assertThat(analisis.estado()).isEqualTo(EstadoDelAnalisis.LISTO);
        assertThat(analisis.texto()).isEqualTo(TEXTO);
        assertThat(analisis.cifrasEnJson()).isEqualTo("{\"cifras\":{}}");
        assertThat(analisis.generadoEn()).isEqualTo(T0.plusSeconds(30));
        assertThat(analisis.tocaIntentarEn(T0.plus(Duration.ofDays(2)), REGLAS)).isFalse();
        assertThatThrownBy(() -> analisis.completar(TEXTO, "{}", FixedClock.at(T0))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> analisis.fallar(REGLAS, FixedClock.at(T0))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("el texto siempre se guarda con sus cifras: sin ellas no se completa")
    void sinCifras() {
        assertThatThrownBy(() -> pendiente().completar(TEXTO, " ", FixedClock.at(T0))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FR-016: cada fallo suma un intento y el siguiente espera; al tercero queda agotado y deja de intentarse")
    void reintentosAcotados() {
        AnalisisDelPeriodo analisis = pendiente();

        analisis.fallar(REGLAS, FixedClock.at(T0));
        assertThat(analisis.intentos()).isEqualTo(1);
        assertThat(analisis.tocaIntentarEn(T0.plus(Duration.ofHours(5)), REGLAS)).isFalse();
        assertThat(analisis.tocaIntentarEn(T0.plus(Duration.ofHours(6)), REGLAS)).isTrue();

        analisis.fallar(REGLAS, FixedClock.at(T0.plus(Duration.ofHours(6))));
        assertThat(analisis.estado()).isEqualTo(EstadoDelAnalisis.PENDIENTE);

        analisis.fallar(REGLAS, FixedClock.at(T0.plus(Duration.ofHours(12))));
        assertThat(analisis.estado()).isEqualTo(EstadoDelAnalisis.AGOTADO);
        assertThat(analisis.tocaIntentarEn(T0.plus(Duration.ofDays(3)), REGLAS)).isFalse();
        assertThat(analisis.texto()).isNull();
    }

    @Test
    @DisplayName("un periodo sin datos suficientes nace decidido: no se reintenta ni se completa")
    void sinDatos() {
        AnalisisDelPeriodo analisis = AnalisisDelPeriodo.sinDatosSuficientes(AnalisisDelPeriodoId.of(UUID.randomUUID()), ANA,
                Formacion.DIPLOMADO, TipoDePeriodo.MES, 3, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 8), FixedClock.at(T0));

        assertThat(analisis.estado()).isEqualTo(EstadoDelAnalisis.SIN_DATOS);
        assertThat(analisis.formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(analisis.tocaIntentarEn(T0.plus(Duration.ofDays(1)), REGLAS)).isFalse();
        assertThatThrownBy(() -> analisis.completar(TEXTO, "{}", FixedClock.at(T0))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("un periodo que termina antes de empezar, o de número cero, no existe")
    void periodoInvalido() {
        assertThatThrownBy(() -> AnalisisDelPeriodo.pendiente(AnalisisDelPeriodoId.of(UUID.randomUUID()), ANA, Formacion.ESCUELA,
                TipoDePeriodo.SEMANA, 1, LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 19), FixedClock.at(T0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
