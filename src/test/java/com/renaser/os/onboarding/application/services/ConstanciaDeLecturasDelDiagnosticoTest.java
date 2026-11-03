package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.DiagnosticoConsultadoEvent;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LectorDelDiagnostico;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spec 026: la constancia de que alguien abrió el diagnóstico de otra persona. */
class ConstanciaDeLecturasDelDiagnosticoTest {

    private static final UserId LIDER = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000c1"));
    private static final UserId ANA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));
    private static final UserId MENTORA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID GRUPO = UUID.fromString("00000000-0000-0000-0000-00000000f001");
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final Instant AHORA = Instant.parse("2026-11-01T15:30:00Z");

    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final IdGenerator ids = mock(IdGenerator.class);
    private final ConstanciaDeLecturasDelDiagnostico constancia = new ConstanciaDeLecturasDelDiagnostico(eventos, ids, FixedClock.at(AHORA));

    @Test
    @DisplayName("publica quién, de quién, desde qué grupo, con qué mentor, con un id propio y el instante de la lectura")
    void publicaLaLectura() {
        when(ids.newId()).thenReturn(ID);

        constancia.dejar(ANA, new LectorDelDiagnostico(LIDER, GRUPO, MENTORA));

        ArgumentCaptor<Object> publicado = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(publicado.capture());
        assertThat(publicado.getValue()).isEqualTo(new DiagnosticoConsultadoEvent(ID, LIDER, ANA, GRUPO, MENTORA, AHORA));
    }

    @Test
    @DisplayName("Administración llega sin grupo ni mentor y también se anota")
    void sinGrupo() {
        when(ids.newId()).thenReturn(ID);

        constancia.dejar(ANA, new LectorDelDiagnostico(LIDER, null, null));

        ArgumentCaptor<Object> publicado = ArgumentCaptor.forClass(Object.class);
        verify(eventos).publishEvent(publicado.capture());
        assertThat(((DiagnosticoConsultadoEvent) publicado.getValue()).grupoId()).isNull();
    }

    @Test
    @DisplayName("la propia persona leyendo su diagnóstico no deja constancia")
    void nadieSeAnotaASiMismo() {
        constancia.dejar(ANA, new LectorDelDiagnostico(ANA, null, null));

        verify(eventos, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }
}
