package com.renaser.os.leadership.domain.model.cumplimiento;

import com.renaser.os.shared.domain.RechazoConCodigoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Los días que el líder pide de una persona: hasta 62 y siempre entre su Día 1 y hoy (spec 056, FR-017). */
class RangoDeDiasTest {

    /** Miércoles 18 de noviembre de 2026 (día local de la persona). */
    private static final LocalDate HOY = LocalDate.of(2026, 11, 18);
    /** Lunes 5 de octubre. */
    private static final LocalDate DIA_UNO = LocalDate.of(2026, 10, 5);

    private static RangoDeDias pedido(String desde, String hasta) {
        return RangoDeDias.pedido(LocalDate.parse(desde), LocalDate.parse(hasta));
    }

    @Test
    @DisplayName("spec 056 FR-017: 62 días justos se piden; 63, o un rango al revés, son RANGO_INVALIDO (400)")
    void elTopeDeDias() {
        assertThat(pedido("2026-10-01", "2026-12-01").hasta()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(pedido("2026-11-18", "2026-11-18").desde()).as("un solo día").isEqualTo(HOY);

        assertThatThrownBy(() -> pedido("2026-10-01", "2026-12-02")).isInstanceOfSatisfying(RechazoConCodigoException.class, e -> {
            assertThat(e.estado()).isEqualTo(400);
            assertThat(e.codigo()).isEqualTo("RANGO_INVALIDO");
            assertThat(e.getMessage()).contains("62");
        });
        assertThatThrownBy(() -> pedido("2026-11-10", "2026-11-01")).isInstanceOfSatisfying(RechazoConCodigoException.class,
                e -> assertThat(e.codigo()).isEqualTo("RANGO_INVALIDO"));
    }

    @Test
    @DisplayName("spec 056 FR-017: lo anterior al Día 1 no se devuelve")
    void seRecortaAlDiaUno() {
        RangoDeDias recortado = pedido("2026-09-28", "2026-10-07").recortadoA(DIA_UNO, HOY).orElseThrow();

        assertThat(recortado.desde()).isEqualTo(DIA_UNO);
        assertThat(recortado.hasta()).isEqualTo(LocalDate.of(2026, 10, 7));
    }

    @Test
    @DisplayName("spec 056 FR-017: lo que está por venir no se devuelve; hoy sí, aunque todavía no cierre")
    void seRecortaAHoy() {
        RangoDeDias recortado = pedido("2026-11-17", "2026-11-30").recortadoA(DIA_UNO, HOY).orElseThrow();

        assertThat(recortado.desde()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(recortado.hasta()).isEqualTo(HOY);
    }

    @Test
    @DisplayName("spec 056 FR-017: un rango que cae entero antes del Día 1 o después de hoy no deja ningún día")
    void sinDiasQueMostrar() {
        assertThat(pedido("2026-09-01", "2026-10-04").recortadoA(DIA_UNO, HOY)).isEmpty();
        assertThat(pedido("2026-11-19", "2026-11-30").recortadoA(DIA_UNO, HOY)).isEmpty();
        assertThat(pedido("2026-10-05", "2026-10-05").recortadoA(DIA_UNO, DIA_UNO)).as("el Día 1 es hoy").isPresent();
    }

    @Test
    @DisplayName("spec 056 FR-017: un rango dentro del programa queda igual")
    void sinCambios() {
        RangoDeDias recortado = pedido("2026-11-14", "2026-11-18").recortadoA(DIA_UNO, HOY).orElseThrow();

        assertThat(recortado).isEqualTo(pedido("2026-11-14", "2026-11-18"));
    }
}
