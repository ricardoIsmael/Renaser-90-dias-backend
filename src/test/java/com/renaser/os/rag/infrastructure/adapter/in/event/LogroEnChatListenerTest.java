package com.renaser.os.rag.infrastructure.adapter.in.event;

import static org.mockito.Mockito.verifyNoInteractions;

import com.renaser.os.shared.application.InterruptoresDePrueba;

import com.renaser.os.habits.api.RachaCompletadaEvent;
import com.renaser.os.rag.application.ports.in.logro.CelebrarLogroEnChatUseCase;
import com.renaser.os.rag.application.ports.in.logro.CelebrarLogroEnChatUseCase.LogroEnChatCommand;
import com.renaser.os.rag.domain.model.logro.TipoLogro;
import com.renaser.os.rocks.api.RocaCompletadaEvent;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

/** Unit puro: cada evento de logro llega al puerto con su tipo y su id como clave. */
@ExtendWith(MockitoExtension.class)
class LogroEnChatListenerTest {

    private static final Instant AHORA = Instant.parse("2026-09-23T02:00:00Z");

    @Mock
    private CelebrarLogroEnChatUseCase useCase;

    private final UserId aprendiz = UserId.of(UUID.randomUUID());

    @Test
    void laRachaCompletadaSeCelebraConSuRachaId() {
        UUID rachaId = UUID.randomUUID();

        new LogroEnChatListener(useCase, InterruptoresDePrueba.conApagadas()).onRachaCompletada(new RachaCompletadaEvent(rachaId, aprendiz, AHORA));

        verify(useCase).celebrarEnElChat(new LogroEnChatCommand(aprendiz, TipoLogro.RACHA_SIN_CELULAR, rachaId));
    }

    @Test
    void laRocaCompletadaSeCelebraConSuRocaId() {
        UUID rocaId = UUID.randomUUID();

        new LogroEnChatListener(useCase, InterruptoresDePrueba.conApagadas()).onRocaCompletada(new RocaCompletadaEvent(rocaId, aprendiz, AHORA));

        verify(useCase).celebrarEnElChat(new LogroEnChatCommand(aprendiz, TipoLogro.ROCA_COMPLETADA, rocaId));
    }

    @Test
    void conSerApagadaNingunLogroSeCelebra() {
        var listener = new LogroEnChatListener(useCase, InterruptoresDePrueba.conApagadas("ia"));

        listener.onRachaCompletada(new RachaCompletadaEvent(UUID.randomUUID(), aprendiz, AHORA));
        listener.onRocaCompletada(new RocaCompletadaEvent(UUID.randomUUID(), aprendiz, AHORA));

        verifyNoInteractions(useCase);
    }
}
