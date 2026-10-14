package com.renaser.os.chat.infrastructure.adapter.in.scheduler;

import com.renaser.os.chat.application.ports.in.ranking.PublicarPodioDeLaSemanaUseCase;
import com.renaser.os.shared.application.InterruptoresDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Spec 002: el podio de los lunes pospone si está apagado el podio, el chat global o el chat entero; como corre cada
 * hora del lunes, lo intenta la hora siguiente. Sin Spring.
 */
class PodioConInterruptoresTest {

    @Test
    @DisplayName("con el podio, el chat global o el chat apagados no publica")
    void apagadoNoPublica() {
        for (String apagada : new String[] {"ranking.podio_semanal", "chat.global", "chat"}) {
            PublicarPodioDeLaSemanaUseCase podio = mock(PublicarPodioDeLaSemanaUseCase.class);

            new PodioDeLaSemanaScheduler(podio, true, InterruptoresDePrueba.conApagadas(apagada)).publicar();

            verifyNoInteractions(podio);
        }
    }
}
