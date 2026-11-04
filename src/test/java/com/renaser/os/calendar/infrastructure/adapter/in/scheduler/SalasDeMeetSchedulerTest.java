package com.renaser.os.calendar.infrastructure.adapter.in.scheduler;

import com.renaser.os.calendar.application.ports.in.meet.SincronizarSalasDeMeetUseCase;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.FixedClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Los procesos en segundo plano de las salas de Meet nacen apagados con su interruptor (spec 040, FR-014, SC-009). */
@ExtendWith(MockitoExtension.class)
class SalasDeMeetSchedulerTest {

    private static final Instant AHORA = Instant.parse("2026-11-05T15:00:00Z");

    @Mock
    private SincronizarSalasDeMeetUseCase sincronizar;
    @Mock
    private Interruptores interruptores;

    // spec 040 FR-014, SC-009
    @Test
    @DisplayName("SC-009: con eventos.meet apagado (así nace), ni el reintento ni el mantenimiento llaman a nada")
    void apagadoSalta() {
        when(interruptores.prendida("eventos.meet")).thenReturn(false);
        SalasDeMeetScheduler scheduler = new SalasDeMeetScheduler(sincronizar, interruptores, FixedClock.at(AHORA));

        scheduler.reintentar();
        scheduler.mantener();

        verify(sincronizar, never()).reintentarLoPendiente(any());
        verify(sincronizar, never()).mantenimiento(any());
    }

    // spec 040 FR-008, FR-017
    @Test
    @DisplayName("FR-008: con eventos.meet prendido, cada minuto reintenta lo pendiente y cada hora mantiene las salas, con la hora del reloj de la aplicación")
    void prendidoCorre() {
        when(interruptores.prendida("eventos.meet")).thenReturn(true);
        SalasDeMeetScheduler scheduler = new SalasDeMeetScheduler(sincronizar, interruptores, FixedClock.at(AHORA));

        scheduler.reintentar();
        scheduler.mantener();

        verify(sincronizar).reintentarLoPendiente(AHORA);
        verify(sincronizar).mantenimiento(AHORA);
    }
}
