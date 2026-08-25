package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionCelulaUseCase;
import com.renaser.os.community.api.CelulaCreadaEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CelulaCreadaChatListenerTest {

    @Mock
    private CrearConversacionCelulaUseCase crearConversacionCelulaUseCase;

    @Test
    void creaLaConversacionDeLaCelulaRecienCreada() {
        var listener = new CelulaCreadaChatListener(crearConversacionCelulaUseCase);
        UUID celulaId = UUID.randomUUID();

        listener.on(new CelulaCreadaEvent(celulaId, Instant.now()));

        verify(crearConversacionCelulaUseCase).crearParaCelula(celulaId);
    }
}
