package com.renaser.os.points.domain.model.puntaje;

import com.renaser.os.points.domain.model.ajuste.ResultadoAjuste;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PuntajeParticipanteTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static PuntajeParticipante nuevo() {
        return PuntajeParticipante.inicial(UserId.of(UUID.randomUUID()), CLOCK);
    }

    @Test
    @DisplayName("todo participante arranca en 100 puntos, 100 coherencia, sin racha (points.ts:7)")
    void arrancaEnCienPuntos() {
        PuntajeParticipante puntaje = nuevo();

        assertThat(puntaje.puntosLiga()).isEqualTo(100);
        assertThat(puntaje.coherencia()).isEqualByComparingTo("100");
        assertThat(puntaje.rachaActual()).isZero();
        assertThat(puntaje.rachaMaxima()).isZero();
    }

    @Test
    @DisplayName("un ajuste positivo suma al saldo sin tocar el piso")
    void ajustePositivoSuma() {
        PuntajeParticipante puntaje = nuevo();

        ResultadoAjuste resultado = puntaje.registrarAjuste(10, CLOCK);

        assertThat(resultado.saldoAnterior()).isEqualTo(100);
        assertThat(resultado.deltaAplicado()).isEqualTo(10);
        assertThat(resultado.saldoPosterior()).isEqualTo(110);
        assertThat(puntaje.puntosLiga()).isEqualTo(110);
    }

    @Test
    @DisplayName("un ajuste negativo que no cruza 0 se aplica completo")
    void ajusteNegativoParcial() {
        PuntajeParticipante puntaje = nuevo();

        ResultadoAjuste resultado = puntaje.registrarAjuste(-30, CLOCK);

        assertThat(resultado.deltaAplicado()).isEqualTo(-30);
        assertThat(resultado.saldoPosterior()).isEqualTo(70);
    }

    @Test
    @DisplayName("piso en 0: repository.ts:1157 GREATEST(league_points + delta, 0) — el " +
            "deltaAplicado real es MENOR que el solicitado cuando cruzaria el piso")
    void pisoEnCeroRecortaElDeltaAplicado() {
        PuntajeParticipante puntaje = nuevo(); // 100

        ResultadoAjuste resultado = puntaje.registrarAjuste(-150, CLOCK);

        assertThat(resultado.deltaSolicitado()).isEqualTo(-150);
        assertThat(resultado.deltaAplicado()).isEqualTo(-100); // 100 -> 0, no -50
        assertThat(resultado.saldoPosterior()).isEqualTo(0);
        assertThat(puntaje.puntosLiga()).isZero();
    }

    @Test
    @DisplayName("una vez en 0, un nuevo ajuste negativo no mueve nada (deltaAplicado 0)")
    void enCeroUnAjusteNegativoNoAplicaNada() {
        PuntajeParticipante puntaje = nuevo();
        puntaje.registrarAjuste(-100, CLOCK); // a 0

        ResultadoAjuste resultado = puntaje.registrarAjuste(-20, CLOCK);

        assertThat(resultado.saldoAnterior()).isZero();
        assertThat(resultado.deltaAplicado()).isZero();
        assertThat(resultado.saldoPosterior()).isZero();
    }

    @Test
    @DisplayName("la coherencia debe estar entre 0 y 100 (CHECK del baseline)")
    void coherenciaFueraDeRangoFalla() {
        PuntajeParticipante puntaje = nuevo();

        assertThatThrownBy(() -> puntaje.actualizarCoherencia(new BigDecimal("100.01"), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> puntaje.actualizarCoherencia(new BigDecimal("-0.01"), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("los bordes exactos del CHECK (0 y 100) son valores validos, no rechazados")
    void coherenciaEnLosBordesExactosEsValida() {
        PuntajeParticipante puntaje = nuevo();

        puntaje.actualizarCoherencia(BigDecimal.ZERO, CLOCK);
        assertThat(puntaje.coherencia()).isEqualByComparingTo("0");

        puntaje.actualizarCoherencia(new BigDecimal("100"), CLOCK);
        assertThat(puntaje.coherencia()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("un dia perfecto suma racha; uno imperfecto la corta a 0 (route.ts:127-128)")
    void rachaSumaOCorta() {
        PuntajeParticipante puntaje = nuevo();

        puntaje.actualizarRachaTrasDia(true, CLOCK);
        puntaje.actualizarRachaTrasDia(true, CLOCK);
        assertThat(puntaje.rachaActual()).isEqualTo(2);
        assertThat(puntaje.rachaMaxima()).isEqualTo(2);

        puntaje.actualizarRachaTrasDia(false, CLOCK);
        assertThat(puntaje.rachaActual()).isZero();
        assertThat(puntaje.rachaMaxima()).isEqualTo(2); // el maximo historico no baja
    }

    @Test
    @DisplayName("el bono de racha corresponde cada 3er dia perfecto consecutivo (points.ts:129-131)")
    void bonoDeRachaCadaTercerDia() {
        PuntajeParticipante puntaje = nuevo();

        assertThat(puntaje.actualizarRachaTrasDia(true, CLOCK)).isFalse(); // racha 1
        assertThat(puntaje.actualizarRachaTrasDia(true, CLOCK)).isFalse(); // racha 2
        assertThat(puntaje.actualizarRachaTrasDia(true, CLOCK)).isTrue();  // racha 3 -> bono
        assertThat(puntaje.actualizarRachaTrasDia(true, CLOCK)).isFalse(); // racha 4
        assertThat(puntaje.actualizarRachaTrasDia(true, CLOCK)).isFalse(); // racha 5
        assertThat(puntaje.actualizarRachaTrasDia(true, CLOCK)).isTrue();  // racha 6 -> bono
    }

    @Test
    @DisplayName("un dia imperfecto nunca dispara bono, aunque la racha caiga en un multiplo de 3")
    void diaImperfectoNuncaDaBono() {
        PuntajeParticipante puntaje = nuevo();

        assertThat(puntaje.actualizarRachaTrasDia(false, CLOCK)).isFalse();
    }

    @Test
    @DisplayName("correspondeBonoDeRacha(0) es false: racha > 0 es parte de la regla")
    void bonoDeRachaEnCeroEsFalse() {
        assertThat(PuntajeParticipante.correspondeBonoDeRacha(0)).isFalse();
    }
}
