package com.renaser.os.habits.domain.model.registro;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 042 (FR-022): el número del día de un hábito cumplido por el teléfono solo sube, sin puntos ni otra hora de cumplimiento. */
class SubirMedicionDelDiaTest {

    private static final Instant CUMPLIDO = Instant.parse("2026-11-05T15:00:00Z");

    private static RegistroHabito cumplidoCon(MedicionDiaria medicion) {
        RegistroHabito registro = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), LocalDate.of(2026, 11, 5), 12, TipoDia.TODOS, true, Instant.EPOCH);
        registro.completar(8, null, null, null, medicion, CUMPLIDO);
        return registro;
    }

    // spec 042 FR-022
    @Test
    @DisplayName("sube el número y el origen, sin tocar los puntos ni la hora en que se cumplió")
    void sube() {
        RegistroHabito registro = cumplidoCon(MedicionDiaria.gps(new BigDecimal("3")));

        registro.subirMedicion(MedicionDiaria.gps(new BigDecimal("7")), CUMPLIDO.plusSeconds(3600));

        assertThat(registro.medicion().valor()).isEqualByComparingTo("7.00");
        assertThat(registro.puntosOtorgados()).isEqualTo(8);
        assertThat(registro.completadoEn()).isEqualTo(CUMPLIDO);
    }

    // spec 042 FR-022, FR-023
    @Test
    @DisplayName("nunca baja, nunca pisa la captura y no sirve para un registro sin cumplir")
    void soloSube() {
        assertThatThrownBy(() -> cumplidoCon(MedicionDiaria.gps(new BigDecimal("7")))
                .subirMedicion(MedicionDiaria.gps(new BigDecimal("7")), CUMPLIDO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cumplidoCon(MedicionDiaria.manualSiHay(new BigDecimal("4")))
                .subirMedicion(MedicionDiaria.gps(new BigDecimal("9")), CUMPLIDO)).isInstanceOf(IllegalStateException.class);
        RegistroHabito pendiente = RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                HabitoId.of(UUID.randomUUID()), LocalDate.of(2026, 11, 5), 12, TipoDia.TODOS, true, Instant.EPOCH);
        assertThatThrownBy(() -> pendiente.subirMedicion(MedicionDiaria.gps(new BigDecimal("9")), CUMPLIDO))
                .isInstanceOf(IllegalStateException.class);
    }
}
