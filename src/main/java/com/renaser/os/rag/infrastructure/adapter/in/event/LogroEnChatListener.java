package com.renaser.os.rag.infrastructure.adapter.in.event;

import com.renaser.os.habits.api.RachaCompletadaEvent;
import com.renaser.os.rag.application.ports.in.logro.CelebrarLogroEnChatUseCase;
import com.renaser.os.rag.application.ports.in.logro.CelebrarLogroEnChatUseCase.LogroEnChatCommand;
import com.renaser.os.rag.domain.model.logro.TipoLogro;
import com.renaser.os.rocks.api.RocaCompletadaEvent;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.application.ProcesosConInterruptor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de los eventos de logro, al lado de los listeners de {@code notifications} que mandan
 * el push ({@code RachaCompletadaNotificationListener}, {@code RocaCompletadaNotificationListener}).
 * Cada {@code @ApplicationModuleListener} tiene su propia publicacion en el outbox: un fallo aca no
 * frena el push ni al reves.
 *
 * <p>Un metodo con nombre propio por evento (no dos {@code on} sobrecargados): el id de cada
 * publicacion en el outbox sale del metodo, y asi no hay duda de que son dos.
 *
 * <p>Solo traduce el evento al comando; si se celebra lo decide el caso de uso (apagado por
 * defecto).
 */
@Component
class LogroEnChatListener {

    private final CelebrarLogroEnChatUseCase celebrarLogroEnChatUseCase;
    private final Interruptores interruptores;

    LogroEnChatListener(CelebrarLogroEnChatUseCase celebrarLogroEnChatUseCase, Interruptores interruptores) {
        this.celebrarLogroEnChatUseCase = celebrarLogroEnChatUseCase;
        this.interruptores = interruptores;
    }

    @ApplicationModuleListener
    void onRachaCompletada(RachaCompletadaEvent event) {
        if (serApagada()) {
            return;
        }
        celebrarLogroEnChatUseCase.celebrarEnElChat(
                new LogroEnChatCommand(event.participanteId(), TipoLogro.RACHA_SIN_CELULAR, event.rachaId()));
    }

    @ApplicationModuleListener
    void onRocaCompletada(RocaCompletadaEvent event) {
        if (serApagada()) {
            return;
        }
        celebrarLogroEnChatUseCase.celebrarEnElChat(
                new LogroEnChatCommand(event.participanteId(), TipoLogro.ROCA_COMPLETADA, event.rocaId()));
    }

    /** Spec 002 (D-267): con SER («ia») apagada, los mensajes automaticos de SER saltan (politica del catalogo). */
    private boolean serApagada() {
        return ProcesosConInterruptor.saltaEsteTurno(interruptores, "ia", "rag.logro-en-chat");
    }
}
