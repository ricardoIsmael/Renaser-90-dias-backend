package com.renaser.os.calendar.infrastructure.adapter.in.scheduler;

import com.renaser.os.calendar.application.ports.in.meet.TranscripcionesDeMeetUseCase;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TranscripcionesDeMeetSchedulerTest {

    private final TranscripcionesDeMeetUseCase useCase = mock(TranscripcionesDeMeetUseCase.class);
    private final Set<String> prendidas = new HashSet<>();
    private final Interruptores interruptores = new Interruptores() {
        @Override
        public boolean prendida(String clave) {
            return prendidas.contains(clave);
        }

        @Override
        public boolean prendidaPara(String clave, UserId actor) {
            return prendida(clave);
        }

        @Override
        public void exigirPrendidaPara(String clave, UserId actor) {
        }
    };
    private final Clock reloj = new Clock() {
        @Override
        public Instant now() {
            return Instant.parse("2026-11-06T03:00:00Z");
        }

        @Override
        public LocalDate today() {
            return LocalDate.of(2026, 11, 6);
        }
    };
    private final TranscripcionesDeMeetScheduler reloj047 = new TranscripcionesDeMeetScheduler(useCase, interruptores, reloj);

    // spec 047 FR-001
    @Test
    @DisplayName("FR-001: apagada de fábrica, o con eventos.meet apagada, el reloj no busca nada")
    void apagadoNoBusca() {
        reloj047.buscar();
        prendidas.add("eventos.meet.transcripcion");
        reloj047.buscar();
        verify(useCase, never()).buscarPendientes(any());

        prendidas.add("eventos.meet");
        reloj047.buscar();
        verify(useCase).buscarPendientes(reloj.now());
    }

    // spec 047 FR-014
    @Test
    @DisplayName("FR-014: la retención corre aunque el interruptor esté apagado: lo guardado se borra a tiempo igual")
    void retencionSiempre() {
        reloj047.retener();
        verify(useCase).aplicarRetencion(reloj.now());
    }
}
