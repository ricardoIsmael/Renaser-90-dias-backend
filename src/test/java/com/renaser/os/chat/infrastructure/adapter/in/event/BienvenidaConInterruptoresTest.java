package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.DarBienvenidaEnGrupoUseCase;
import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import com.renaser.os.shared.application.InterruptoresDePrueba;
import com.renaser.os.shared.domain.ProcesoPospuestoException;
import com.renaser.os.shared.infrastructure.interruptores.TodoPrendido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Spec 002, columna «Proceso»: con la bienvenida apagada ({@code admin.bienvenida}) las bienvenidas saltan; con el chat
 * apagado se posponen (el oyente falla y el outbox lo reentrega). Sin Spring.
 */
class BienvenidaConInterruptoresTest {

    private final DarBienvenidaEnGrupoUseCase bienvenidas = mock(DarBienvenidaEnGrupoUseCase.class);
    private final ComposicionDeCelulaCambiadaEvent evento =
            new ComposicionDeCelulaCambiadaEvent(UUID.randomUUID(), Instant.parse("2026-10-13T15:00:00Z"));

    @Test
    @DisplayName("todo prendido: da la bienvenida")
    void prendido() {
        new ComposicionCelulaBienvenidaListener(bienvenidas, new TodoPrendido()).on(evento);

        verify(bienvenidas).darBienvenidas(evento.celulaId());
    }

    @Test
    @DisplayName("con admin.bienvenida apagada salta: no da la bienvenida y no falla (no se reintenta)")
    void bienvenidaApagadaSalta() {
        new ComposicionCelulaBienvenidaListener(bienvenidas, InterruptoresDePrueba.conApagadas("admin.bienvenida"))
                .on(evento);

        verifyNoInteractions(bienvenidas);
    }

    @Test
    @DisplayName("con el chat apagado se pospone: lanza para que el outbox la reentregue")
    void chatApagadoPospone() {
        ComposicionCelulaBienvenidaListener oyente =
                new ComposicionCelulaBienvenidaListener(bienvenidas, InterruptoresDePrueba.conApagadas("chat"));

        assertThatThrownBy(() -> oyente.on(evento)).isInstanceOf(ProcesoPospuestoException.class);
        verifyNoInteractions(bienvenidas);
    }
}
