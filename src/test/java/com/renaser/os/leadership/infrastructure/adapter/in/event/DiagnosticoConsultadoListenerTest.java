package com.renaser.os.leadership.infrastructure.adapter.in.event;

import com.renaser.os.leadership.application.ports.in.RegistrarLecturaDelDiagnosticoUseCase;
import com.renaser.os.onboarding.api.DiagnosticoConsultadoEvent;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.events.ApplicationModuleListener;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DiagnosticoConsultadoListenerTest {

    @Test
    @DisplayName("lleva el evento al caso de uso, y lo hace después del commit de la lectura (ApplicationModuleListener)")
    void llevaElEvento() throws Exception {
        RegistrarLecturaDelDiagnosticoUseCase registrar = mock(RegistrarLecturaDelDiagnosticoUseCase.class);
        DiagnosticoConsultadoListener oyente = new DiagnosticoConsultadoListener(registrar);
        DiagnosticoConsultadoEvent evento = new DiagnosticoConsultadoEvent(UUID.randomUUID(), UserId.of(UUID.randomUUID()), UserId.of(UUID.randomUUID()),
                null, null, Instant.parse("2026-11-01T15:30:00Z"));

        oyente.on(evento);

        verify(registrar).registrar(evento);
        Method on = DiagnosticoConsultadoListener.class.getDeclaredMethod("on", DiagnosticoConsultadoEvent.class);
        assertThat(on.isAnnotationPresent(ApplicationModuleListener.class)).isTrue();
    }
}
