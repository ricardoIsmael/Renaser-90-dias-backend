package com.renaser.os.onboarding.domain.model.pagos;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CuotaTest {

    private static final Instant CREADA = Instant.parse("2026-11-01T15:00:00Z");
    private static final Instant MARCADA = Instant.parse("2026-11-20T15:30:00Z");
    private static final UserId ADMIN = UserId.of(UUID.randomUUID());

    private static Cuota cuota(String fecha, String monto) {
        return Cuota.nueva(UUID.randomUUID(), LocalDate.parse(fecha), new BigDecimal(monto), CREADA);
    }

    @Test
    @DisplayName("una cuota nueva nace pendiente, sin quién ni cuándo, y su monto queda con dos decimales")
    void nuevaNacePendiente() {
        Cuota cuota = cuota("2026-11-15", "1000");

        assertThat(cuota.estado()).isEqualTo(EstadoDeCuota.PENDIENTE);
        assertThat(cuota.pagada()).isFalse();
        assertThat(cuota.estadoCambiadoPor()).isNull();
        assertThat(cuota.estadoCambiadoEn()).isNull();
        assertThat(cuota.monto()).isEqualTo(new BigDecimal("1000.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "0.00"})
    @DisplayName("el monto tiene que ser mayor que cero")
    void montoPositivo(String monto) {
        assertThatThrownBy(() -> cuota("2026-11-15", monto)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mayor que cero");
    }

    @Test
    @DisplayName("el monto admite dos decimales, no tres, y un tope de 9 999 999,99")
    void montoConDosDecimalesYTope() {
        assertThat(cuota("2026-11-15", "333.33").monto()).isEqualTo(new BigDecimal("333.33"));
        assertThat(cuota("2026-11-15", "250.50").monto()).isEqualTo(new BigDecimal("250.50"));
        assertThat(cuota("2026-11-15", "1000.000").monto()).as("ceros de sobra no cuentan como decimales").isEqualTo(new BigDecimal("1000.00"));
        assertThatThrownBy(() -> cuota("2026-11-15", "333.333")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("dos decimales");
        assertThat(cuota("2026-11-15", "9999999.99").monto()).isEqualTo(new BigDecimal("9999999.99"));
        assertThatThrownBy(() -> cuota("2026-11-15", "10000000")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("9 999 999,99");
    }

    @Test
    @DisplayName("la fecha es obligatoria y tiene que ser de un año razonable (2020 a 2100)")
    void fechaRazonable() {
        assertThatThrownBy(() -> Cuota.nueva(UUID.randomUUID(), null, BigDecimal.TEN, CREADA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cuota("2019-12-31", "10")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("año");
        assertThatThrownBy(() -> cuota("2101-01-01", "10")).isInstanceOf(IllegalArgumentException.class);
        assertThat(cuota("2020-01-01", "10").venceEl()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(cuota("2100-12-31", "10").venceEl()).isEqualTo(LocalDate.of(2100, 12, 31));
    }

    @Test
    @DisplayName("marcarla pagada deja quién y cuándo; devolverla a pendiente deja quién y cuándo de esa vuelta")
    void marcarYDevolver() {
        Cuota pagada = cuota("2026-11-15", "1000").marcada(EstadoDeCuota.PAGADA, ADMIN, MARCADA);

        assertThat(pagada.pagada()).isTrue();
        assertThat(pagada.estadoCambiadoPor()).isEqualTo(ADMIN);
        assertThat(pagada.estadoCambiadoEn()).isEqualTo(MARCADA);

        Instant despues = MARCADA.plusSeconds(3600);
        UserId otro = UserId.of(UUID.randomUUID());
        Cuota devuelta = pagada.marcada(EstadoDeCuota.PENDIENTE, otro, despues);
        assertThat(devuelta.pagada()).isFalse();
        assertThat(devuelta.estadoCambiadoPor()).isEqualTo(otro);
        assertThat(devuelta.estadoCambiadoEn()).isEqualTo(despues);
    }

    @Test
    @DisplayName("marcar como ya está es idempotente: no cambia quién ni cuándo (el doble toque deja una sola marca)")
    void marcarDosVecesEsLoMismo() {
        Cuota pagada = cuota("2026-11-15", "1000").marcada(EstadoDeCuota.PAGADA, ADMIN, MARCADA);

        Cuota otraVez = pagada.marcada(EstadoDeCuota.PAGADA, UserId.of(UUID.randomUUID()), MARCADA.plusSeconds(1));

        assertThat(otraVez).isSameAs(pagada);
        assertThat(cuota("2026-11-15", "10").marcada(EstadoDeCuota.PENDIENTE, ADMIN, MARCADA).estadoCambiadoPor()).as("pendiente sobre pendiente tampoco anota nada").isNull();
    }

    @Test
    @DisplayName("una pagada no dice «pagada» sin decir cuándo")
    void pagadaDiceCuando() {
        assertThatThrownBy(() -> new Cuota(UUID.randomUUID(), LocalDate.of(2026, 11, 15), BigDecimal.TEN, EstadoDeCuota.PAGADA, null, null, CREADA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("la fecha y el monto se cambian en una pendiente y no en una pagada (409)")
    void editarSoloPendientes() {
        Cuota pendiente = cuota("2026-11-15", "1000");
        Cuota editada = pendiente.conFechaYMonto(LocalDate.of(2026, 12, 1), new BigDecimal("900"));

        assertThat(editada.id()).isEqualTo(pendiente.id());
        assertThat(editada.venceEl()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(editada.monto()).isEqualTo(new BigDecimal("900.00"));

        Cuota pagada = pendiente.marcada(EstadoDeCuota.PAGADA, ADMIN, MARCADA);
        assertThatThrownBy(() -> pagada.conFechaYMonto(LocalDate.of(2026, 12, 1), BigDecimal.ONE)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pagada");
    }
}
