package com.renaser.os.rag.infrastructure.adapter.in.event;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.SemanaDelSemaforoCerradaEvent;
import com.renaser.os.rag.application.ports.in.semaforo.DejarSemaforoEnChatUseCase;
import com.renaser.os.rag.application.ports.in.semaforo.DejarSemaforoEnChatUseCase.SemaforoEnChatCommand;
import com.renaser.os.rag.domain.model.semaforo.ColorDeLaSemana;
import com.renaser.os.rag.domain.model.semaforo.SemaforoEnChat.CierreDeSemana;
import com.renaser.os.shared.domain.UserId;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor del cierre semanal del semáforo, al lado del listener de {@code notifications} que manda
 * el push sin cifras ({@code SemanaDelSemaforoCerradaNotificationListener}). Cada
 * {@code @ApplicationModuleListener} tiene su propia publicación en el outbox: un fallo acá no frena
 * el push ni al revés.
 *
 * <p>Solo traduce el evento al comando; si se escribe lo decide el caso de uso (apagado por defecto).
 */
@Component
class SemaforoEnChatListener {

    private final DejarSemaforoEnChatUseCase dejarSemaforoEnChatUseCase;

    SemaforoEnChatListener(DejarSemaforoEnChatUseCase dejarSemaforoEnChatUseCase) {
        this.dejarSemaforoEnChatUseCase = dejarSemaforoEnChatUseCase;
    }

    @ApplicationModuleListener
    void onSemanaCerrada(SemanaDelSemaforoCerradaEvent event) {
        CierreDeSemana cierre = new CierreDeSemana(espejo(event.color()), event.color().etiqueta(),
                event.porcentaje());
        dejarSemaforoEnChatUseCase.dejarEnElChat(
                new SemaforoEnChatCommand(UserId.of(event.participanteId()), cierre, event.claveDeduplicacion()));
    }

    /** Exhaustivo a propósito: un color nuevo en {@code points} rompe la compilación, no el outbox. */
    static ColorDeLaSemana espejo(ColorSemaforo color) {
        return switch (color) {
            case VERDE -> ColorDeLaSemana.VERDE;
            case AMARILLO -> ColorDeLaSemana.AMARILLO;
            case ROJO -> ColorDeLaSemana.ROJO;
            case SIN_DATOS -> ColorDeLaSemana.SIN_DATOS;
        };
    }
}
