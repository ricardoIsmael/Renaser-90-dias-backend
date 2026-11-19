package com.renaser.os.leadership.domain.model.documento;

import com.renaser.os.shared.domain.RechazoConCodigoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La semana de un documento de mentor (spec 056, FR-036): sábado a viernes, también la que corre, sin tope en un Día 1. */
class SemanaDelDocumentoTest {

    /** Miércoles 18 de noviembre de 2026. */
    private static final LocalDate HOY = LocalDate.of(2026, 11, 18);

    @Test
    @DisplayName("spec 056 FR-036: sin fecha es la semana en curso: el viernes que viene si hoy no lo es, y el de hoy si hoy es viernes")
    void sinFechaEsLaSemanaEnCurso() {
        assertThat(SemanaDelDocumento.viernes(null, HOY)).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(SemanaDelDocumento.viernes(null, LocalDate.of(2026, 11, 20))).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(SemanaDelDocumento.viernes(null, LocalDate.of(2026, 11, 21))).as("el sábado empieza la semana siguiente")
                .isEqualTo(LocalDate.of(2026, 11, 27));
    }

    @Test
    @DisplayName("spec 056 FR-036: con fecha tiene que ser un viernes: cualquier otro día es SEMANA_INVALIDA (400)")
    void tieneQueSerViernes() {
        assertThat(SemanaDelDocumento.viernes(LocalDate.of(2026, 11, 13), HOY)).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThatThrownBy(() -> SemanaDelDocumento.viernes(LocalDate.of(2026, 11, 17), HOY))
                .isInstanceOfSatisfying(RechazoConCodigoException.class, e -> {
                    assertThat(e.codigo()).isEqualTo("SEMANA_INVALIDA");
                    assertThat(e.estado()).isEqualTo(400);
                });
    }

    @Test
    @DisplayName("spec 056 FR-036: una semana cuyo sábado todavía no llegó es FUERA_DEL_PROGRAMA; la que corre hoy sí se puede pedir")
    void noHaySemanasFuturas() {
        assertThatThrownBy(() -> SemanaDelDocumento.viernes(LocalDate.of(2026, 11, 27), HOY))
                .isInstanceOfSatisfying(RechazoConCodigoException.class, e -> assertThat(e.codigo()).isEqualTo("FUERA_DEL_PROGRAMA"));
        assertThat(SemanaDelDocumento.viernes(LocalDate.of(2026, 11, 20), HOY)).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    @DisplayName("spec 056 FR-036: una semana muy vieja se puede pedir: lo que cuenta es el grupo del mentor, no su propio Día 1")
    void sinTopeHaciaAtras() {
        assertThat(SemanaDelDocumento.viernes(LocalDate.of(2026, 1, 2), HOY)).isEqualTo(LocalDate.of(2026, 1, 2));
    }
}
