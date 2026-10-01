package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarReporteDeMentoresUseCase.EntradaDelReporte;
import com.renaser.os.leadership.application.ports.in.ConsultarReporteDeMentoresUseCase.ReporteDeMentores;
import com.renaser.os.leadership.application.ports.in.RegistrarObservacionUseCase.RegistrarObservacionCommand;
import com.renaser.os.shared.domain.NotAuthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.LIDER;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.LUISA;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.MARTA;
import static com.renaser.os.leadership.application.services.PadronDeMentoresServiceTest.RAUL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El reporte del mes (SDD 002, RL-19..RL-23; D-241). */
class ReporteDeMentoresServiceTest {

    private final BancoDeLiderazgo banco = new BancoDeLiderazgo();

    @BeforeEach
    void preparar() {
        PadronDeMentoresServiceTest.prepararCuerpo(banco);
        banco.evaluacion(RAUL, "92.5");
    }

    private static String nombre(EntradaDelReporte e) {
        return e.indicadores().nombre();
    }

    @Test
    @DisplayName("RL-23: ordena por evaluacion y deja aparte, nombrado, a quien no tiene muestra")
    void ordenYExcluidos() {
        ReporteDeMentores reporte = banco.reporte().reporte(LIDER, null);

        assertThat(reporte.ordenados()).extracting(ReporteDeMentoresServiceTest::nombre)
                .containsExactly("Raúl Soto", "Luisa Rojas");
        assertThat(reporte.sinMuestra()).extracting(ReporteDeMentoresServiceTest::nombre)
                .containsExactly("Marta Ruiz");
        assertThat(reporte.mes()).isEqualTo("2026-09");
        assertThat(reporte.cerrado()).isFalse();
        assertThat(reporte.respondidasSinAtribucion()).isEqualTo(2);
    }

    @Test
    @DisplayName("RL-19: cuenta lo que el lider le dijo a cada mentor en el mes, por tipo")
    void observacionesDelMes() {
        var registrar = banco.observacionesService();
        registrar.registrar(new RegistrarObservacionCommand(LIDER, LUISA, "ALERTA", "x", false, null, "k1"));
        registrar.registrar(new RegistrarObservacionCommand(LIDER, LUISA, "RECONOCIMIENTO", "y", false, null, "k2"));

        ReporteDeMentores reporte = banco.reporte().reporte(LIDER, null);

        EntradaDelReporte luisa = reporte.ordenados().stream()
                .filter(e -> e.indicadores().mentorId().equals(LUISA)).findFirst().orElseThrow();
        assertThat(luisa.observaciones().valor().alertas()).isEqualTo(1);
        assertThat(luisa.observaciones().valor().reconocimientos()).isEqualTo(1);
        EntradaDelReporte marta = reporte.sinMuestra().getFirst();
        assertThat(marta.indicadores().mentorId()).isEqualTo(MARTA);
        assertThat(marta.observaciones().valor().total()).isZero();
    }

    @Test
    @DisplayName("un mes pasado sale cerrado y se evalua ese mes")
    void mesPasado() {
        ReporteDeMentores reporte = banco.reporte().reporte(LIDER, "2026-08");

        assertThat(reporte.cerrado()).isTrue();
        assertThat(banco.mesesEvaluados).containsOnly(YearMonth.of(2026, 8));
    }

    @Test
    @DisplayName("RL-26: un MENTOR no lee el reporte")
    void mentorNoLee() {
        assertThatThrownBy(() -> banco.reporte().reporte(LUISA, null)).isInstanceOf(NotAuthorizedException.class);
    }
}
