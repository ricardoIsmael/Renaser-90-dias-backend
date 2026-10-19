package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.registro.ContarCumplimientoPorHabitoPort;
import com.renaser.os.habits.domain.model.registro.CumplimientoDeUnHabito;
import com.renaser.os.points.api.CumplimientoDeHabito;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D-277: lo que {@code habits} le reporta al mensaje del semáforo, con la regla de «contaba» de siempre. */
class CumplimientoPorHabitoFinderServiceTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    private static final LocalDate DESDE = LocalDate.of(2026, 10, 12);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 18);

    @Test
    @DisplayName("programados es lo que contaba (registros menos opcionales sin cumplir) y cumplidos los completados")
    void reportaLoQueContaba() {
        ContarCumplimientoPorHabitoPort puerto = (persona, desde, hasta) -> List.of(
                new CumplimientoDeUnHabito("AGUA", 7, 6, 0),
                new CumplimientoDeUnHabito("LECTURA", 7, 2, 3));

        List<CumplimientoDeHabito> resultado = new CumplimientoPorHabitoFinderService(puerto)
                .enRango(PERSONA, DESDE, HASTA);

        assertThat(resultado).containsExactly(
                new CumplimientoDeHabito("AGUA", 7, 6),
                new CumplimientoDeHabito("LECTURA", 4, 2));
    }

    @Test
    @DisplayName("un opcional cumplido cuenta (suma al numerador y al denominador); sin registros no devuelve nada")
    void unOpcionalCumplidoCuentaYSinRegistrosNoHayNada() {
        ContarCumplimientoPorHabitoPort conOpcional = (persona, desde, hasta) -> List.of(
                new CumplimientoDeUnHabito("EXTRA", 5, 2, 3));
        ContarCumplimientoPorHabitoPort vacio = (persona, desde, hasta) -> List.of();

        assertThat(new CumplimientoPorHabitoFinderService(conOpcional).enRango(PERSONA, DESDE, HASTA))
                .containsExactly(new CumplimientoDeHabito("EXTRA", 2, 2));
        assertThat(new CumplimientoPorHabitoFinderService(vacio).enRango(PERSONA, DESDE, HASTA)).isEmpty();
    }

    @Test
    @DisplayName("el puerto no puede devolver más completados y opcionales sin cumplir que registros")
    void cuentasImposiblesSeRechazan() {
        assertThatThrownBy(() -> new CumplimientoDeUnHabito("AGUA", 3, 3, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
