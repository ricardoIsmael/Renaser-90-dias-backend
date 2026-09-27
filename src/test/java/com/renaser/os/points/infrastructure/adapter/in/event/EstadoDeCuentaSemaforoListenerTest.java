package com.renaser.os.points.infrastructure.adapter.in.event;

import com.renaser.os.points.application.ports.in.semaforo.RegistrarSuspensionDelSemaforoUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.EstadoDeCuentaCambiadoEvent;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit puro (sin Spring): qué cambio de estado le interesa al semáforo (D-209). Que el outbox entregue
 * el evento de verdad y la suspensión llegue a la base lo prueba {@code SuspensionEnElSemaforoIT}.
 */
@ExtendWith(MockitoExtension.class)
class EstadoDeCuentaSemaforoListenerTest {

    /** Lunes 21 a las 21:00 de Lima: el instante viaja tal cual, el día lo decide el caso de uso. */
    private static final Instant INSTANTE = Instant.parse("2026-09-22T02:00:00Z");

    @Mock
    private RegistrarSuspensionDelSemaforoUseCase suspensiones;

    private final UserId ana = UserId.of(UUID.randomUUID());

    @Test
    @DisplayName("suspender anota la suspensión con el instante del evento")
    void suspenderAnotaLaSuspension() {
        new EstadoDeCuentaSemaforoListener(suspensiones)
                .on(new EstadoDeCuentaCambiadoEvent(ana, UserStatus.ACTIVE, UserStatus.SUSPENDED, INSTANTE));

        verify(suspensiones).alSuspender(ana, INSTANTE);
    }

    @Test
    @DisplayName("suspender una cuenta que no estaba activa también cuenta")
    void suspenderDesdeSinAprobarTambienAnota() {
        new EstadoDeCuentaSemaforoListener(suspensiones)
                .on(new EstadoDeCuentaCambiadoEvent(ana, UserStatus.INACTIVE, UserStatus.SUSPENDED, INSTANTE));

        verify(suspensiones).alSuspender(ana, INSTANTE);
    }

    @Test
    @DisplayName("reactivar termina la suspensión")
    void reactivarTerminaLaSuspension() {
        new EstadoDeCuentaSemaforoListener(suspensiones)
                .on(new EstadoDeCuentaCambiadoEvent(ana, UserStatus.SUSPENDED, UserStatus.ACTIVE, INSTANTE));

        verify(suspensiones).alReactivar(ana, INSTANTE);
    }

    @Test
    @DisplayName("un cambio que no entra ni sale de la suspensión no toca el semáforo")
    void otroCambioNoTocaElSemaforo() {
        new EstadoDeCuentaSemaforoListener(suspensiones)
                .on(new EstadoDeCuentaCambiadoEvent(ana, UserStatus.INACTIVE, UserStatus.ACTIVE, INSTANTE));

        verifyNoInteractions(suspensiones);
    }
}
