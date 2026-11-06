package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.recorrido.RecorridoDeBienvenidaUseCase.ComoTermino;
import com.renaser.os.onboarding.application.ports.out.recorrido.RecorridoDeBienvenidaPort;
import com.renaser.os.onboarding.application.ports.out.recorrido.RecorridoDeBienvenidaPort.FilaDelRecorrido;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.Test;

import com.renaser.os.shared.domain.FixedClock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** spec 045 FR-001, FR-009, SC-003: cuándo sale el recorrido, en qué paso va y cómo termina. */
class RecorridoDeBienvenidaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-06T15:00:00Z");
    private final UserId persona = UserId.of(UUID.randomUUID());
    private final RecorridoDeBienvenidaPort port = mock(RecorridoDeBienvenidaPort.class);
    private final ParticipacionProgramaFinder participaciones = mock(ParticipacionProgramaFinder.class);
    private final RecorridoDeBienvenidaService service =
            new RecorridoDeBienvenidaService(port, participaciones, FixedClock.at(AHORA));

    private void conPrograma(boolean activado, boolean suspendido, UserRole rol) {
        when(participaciones.deParticipante(persona)).thenReturn(Optional.of(new ParticipacionPrograma(persona, true, 0,
                null, ZoneOffset.UTC, null, null, null, rol, suspendido, activado)));
    }

    // spec 045 FR-001, FR-009
    @Test
    void cuentaNuevaConProgramaActivadoLoVeDesdeElPasoUno() {
        conPrograma(true, false, UserRole.TRAINEE);
        when(port.de(persona)).thenReturn(Optional.empty());
        var vista = service.de(persona);
        assertThat(vista.mostrar()).isTrue();
        assertThat(vista.paso()).isEqualTo(1);
    }

    // spec 045 FR-001, SC-003 (D2: solo cuentas nuevas)
    @Test
    void cuentaAnteriorNoLoVe() {
        conPrograma(true, false, UserRole.TRAINEE);
        when(port.de(persona)).thenReturn(Optional.of(new FilaDelRecorrido(5, AHORA, ComoTermino.CUENTA_ANTERIOR)));
        assertThat(service.de(persona).mostrar()).isFalse();
    }

    // spec 045 FR-001 (D4: el personal solo si hace su programa propio)
    @Test
    void sinProgramaPropioOSinActivarNoLoVe() {
        when(port.de(persona)).thenReturn(Optional.empty());
        when(participaciones.deParticipante(persona)).thenReturn(Optional.empty());
        assertThat(service.de(persona).mostrar()).isFalse();
        conPrograma(false, false, UserRole.MENTOR);
        assertThat(service.de(persona).mostrar()).isFalse();
        conPrograma(true, true, UserRole.TRAINEE);
        assertThat(service.de(persona).mostrar()).isFalse();
        conPrograma(true, false, UserRole.MENTOR);
        assertThat(service.de(persona).mostrar()).isTrue();
    }

    // spec 045 FR-001: retomar en el paso donde quedó
    @Test
    void recuerdaElPaso() {
        conPrograma(true, false, UserRole.TRAINEE);
        when(port.de(persona)).thenReturn(Optional.of(new FilaDelRecorrido(3, null, null)));
        service.avanzar(persona, 3);
        verify(port).guardarPaso(persona, 3, AHORA);
        assertThat(service.de(persona).paso()).isEqualTo(3);
        assertThat(service.de(persona).mostrar()).isTrue();
    }

    // spec 045 FR-004
    @Test
    void terminarConSaltoONoAceptaCuentaAnterior() {
        conPrograma(true, false, UserRole.TRAINEE);
        when(port.de(persona)).thenReturn(Optional.of(new FilaDelRecorrido(2, AHORA, ComoTermino.SALTO)));
        assertThat(service.terminar(persona, ComoTermino.SALTO).mostrar()).isFalse();
        verify(port).terminar(persona, ComoTermino.SALTO, AHORA);
        assertThatThrownBy(() -> service.terminar(persona, ComoTermino.CUENTA_ANTERIOR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.avanzar(persona, 6)).isInstanceOf(IllegalArgumentException.class);
        verify(port, never()).guardarPaso(any(), anyInt(), any());
    }
}
