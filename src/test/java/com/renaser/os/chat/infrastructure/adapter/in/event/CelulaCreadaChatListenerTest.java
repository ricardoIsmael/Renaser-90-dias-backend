package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionCelulaUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.SincronizarParticipantesCelulaUseCase;
import com.renaser.os.community.api.CelulaCreadaEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CelulaCreadaChatListenerTest {

    @Mock
    private CrearConversacionCelulaUseCase crearConversacionCelulaUseCase;
    @Mock
    private SincronizarParticipantesCelulaUseCase sincronizarParticipantes;

    @Test
    void creaLaConversacionDeLaCelulaRecienCreada() {
        var listener = new CelulaCreadaChatListener(crearConversacionCelulaUseCase, sincronizarParticipantes);
        UUID celulaId = UUID.randomUUID();

        listener.on(new CelulaCreadaEvent(celulaId, Instant.now()));

        verify(crearConversacionCelulaUseCase).crearParaCelula(celulaId);
    }

    @Test
    @DisplayName("E-625: después de crear la conversación reconcilia los integrantes (el aviso de composición pudo "
            + "llegar antes y no encontrarla)")
    void sincronizaLosIntegrantesDespuesDeCrearLaConversacion() {
        var listener = new CelulaCreadaChatListener(crearConversacionCelulaUseCase, sincronizarParticipantes);
        UUID celulaId = UUID.randomUUID();

        listener.on(new CelulaCreadaEvent(celulaId, Instant.now()));

        InOrder orden = inOrder(crearConversacionCelulaUseCase, sincronizarParticipantes);
        orden.verify(crearConversacionCelulaUseCase).crearParaCelula(celulaId);
        orden.verify(sincronizarParticipantes).sincronizar(celulaId);
    }
}
