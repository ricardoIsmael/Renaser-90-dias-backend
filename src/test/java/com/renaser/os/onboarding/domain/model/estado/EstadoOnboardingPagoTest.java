package com.renaser.os.onboarding.domain.model.estado;

import com.renaser.os.onboarding.domain.model.pagos.ModalidadDePago;
import com.renaser.os.onboarding.domain.model.pagos.Moneda;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El pago anotado en el estado de ingreso de la persona (spec 032). */
class EstadoOnboardingPagoTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-11-03T15:00:00Z"));

    private static UserId persona() {
        return UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("todo estado nuevo nace sin pago anotado: las cuentas de antes no muestran «Mi ingreso»")
    void nacenSinAnotar() {
        assertThat(EstadoOnboarding.iniciar(persona(), CLOCK).pago().anotado()).isFalse();
        assertThat(EstadoOnboarding.conDiagnostico(persona(), persona(), CLOCK).pago().anotado()).isFalse();
        assertThat(EstadoOnboarding.sinEmpezar(persona(), CLOCK).pago().anotado()).isFalse();
    }

    @Test
    @DisplayName("la fila que nace porque Administración anota el pago todavía no empezó, y es de la ficha estándar")
    void sinEmpezarNoEstaIniciado() {
        EstadoOnboarding estado = EstadoOnboarding.sinEmpezar(persona(), CLOCK);

        assertThat(estado.iniciadoEn()).isNull();
        assertThat(estado.conDiagnostico()).isFalse();
        estado.registrarInicio(CLOCK);
        assertThat(estado.iniciadoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    @DisplayName("anotar «sin cronograma» y anotar «con cronograma» (con su moneda) cambian la modalidad")
    void anotar() {
        EstadoOnboarding estado = EstadoOnboarding.sinEmpezar(persona(), CLOCK);

        estado.anotarSinCronograma(CLOCK);
        assertThat(estado.pago().modalidad()).isEqualTo(ModalidadDePago.SIN_CRONOGRAMA);
        assertThat(estado.pago().moneda()).isNull();

        estado.anotarConCronograma(Moneda.USD, CLOCK);
        assertThat(estado.pago().modalidad()).isEqualTo(ModalidadDePago.CON_CRONOGRAMA);
        assertThat(estado.pago().moneda()).isEqualTo(Moneda.USD);
    }

    @Test
    @DisplayName("confirmar deja nombre, hora y huella; volver a asignar un cronograma borra la confirmación (era de otro)")
    void confirmarYReasignar() {
        EstadoOnboarding estado = EstadoOnboarding.sinEmpezar(persona(), CLOCK);
        estado.anotarConCronograma(Moneda.PEN, CLOCK);

        estado.confirmarCronograma("Ana Quispe", "abc", CLOCK);
        assertThat(estado.pago().confirmadoNombre()).isEqualTo("Ana Quispe");
        assertThat(estado.pago().confirmadoEn()).isEqualTo(CLOCK.now());

        estado.anotarConCronograma(Moneda.PEN, CLOCK);
        assertThat(estado.pago().confirmadoEn()).isNull();
        assertThat(estado.pago().confirmadoHuella()).isNull();
    }

    @Test
    @DisplayName("sin un cronograma anotado no hay nada que confirmar (409)")
    void sinCronogramaNoSeConfirma() {
        EstadoOnboarding estado = EstadoOnboarding.sinEmpezar(persona(), CLOCK);
        assertThatThrownBy(() -> estado.confirmarCronograma("Ana", "abc", CLOCK)).isInstanceOf(IllegalStateException.class);
        estado.anotarSinCronograma(CLOCK);
        assertThatThrownBy(() -> estado.confirmarCronograma("Ana", "abc", CLOCK)).isInstanceOf(IllegalStateException.class);
    }
}
