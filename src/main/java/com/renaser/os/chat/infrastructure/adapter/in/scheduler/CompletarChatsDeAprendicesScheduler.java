package com.renaser.os.chat.infrastructure.adapter.in.scheduler;

import com.renaser.os.chat.application.ports.in.conversacion.CompletarChatsDeAprendicesUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.CompletarChatsDeAprendicesUseCase.ResultadoCompletar;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barrido que completa los chats de los aprendices (D-224): su soporte y su chat de dos con quien lo
 * acompaña. Corre a los pocos minutos de arrancar —así, al desplegar, los aprendices anteriores quedan
 * cubiertos sin que nadie tenga que acordarse de llamar a un endpoint— y después cada hora.
 *
 * <p><b>Por qué periódico y no una sola vez al arrancar.</b> Una corrida única cubre a los de hoy, pero
 * hay huecos que siguen abriéndose solos: un grupo cuyo período empieza sin que nadie lo toque no
 * publica ningún evento, y un chat que falló después de que el outbox dio el evento por atendido no se
 * reintenta. Cada hora, sin mensajes y sin crear nada que ya exista, cuesta unas pocas consultas.
 *
 * <p>No depende del día local de nadie (regla 02 §1): no hay "hoy" en esta regla, solo quién está.
 *
 * <p>{@code @SchedulerLock} (C-5, fila 14): con N instancias no duplicaría nada —el UNIQUE de
 * {@code conversaciones.clave_directa} lo impide—, pero recorrería el padrón dos veces.
 *
 * <p>Se apaga con {@code renaser.scheduling.chats-de-aprendices.activo=false} (lo hacen las pruebas:
 * un barrido que dispara solo en medio de la suite le crearía chats a los datos de otra prueba).
 */
@Component
@ConditionalOnProperty(name = "renaser.scheduling.chats-de-aprendices.activo", havingValue = "true",
        matchIfMissing = true)
public class CompletarChatsDeAprendicesScheduler {

    private static final Logger log = LoggerFactory.getLogger(CompletarChatsDeAprendicesScheduler.class);

    private final CompletarChatsDeAprendicesUseCase completarChats;

    public CompletarChatsDeAprendicesScheduler(CompletarChatsDeAprendicesUseCase completarChats) {
        this.completarChats = completarChats;
    }

    @Scheduled(initialDelayString = "${renaser.scheduling.chats-de-aprendices.primera-corrida:PT3M}",
            fixedDelayString = "${renaser.scheduling.chats-de-aprendices.cada:PT1H}")
    @SchedulerLock(name = "chat-completar-chats-de-aprendices",
            lockAtMostFor = "${renaser.scheduling.shedlock.chat-completar-chats-de-aprendices.lock-at-most-for:PT20M}",
            lockAtLeastFor = "${renaser.scheduling.shedlock.chat-completar-chats-de-aprendices.lock-at-least-for:PT30S}")
    public void completar() {
        ResultadoCompletar resultado = completarChats.completarTodos();
        if (resultado.incompleto()) {
            log.warn("[chat.CompletarChatsDeAprendicesScheduler] quedaron pendientes: {} soportes y {} grupos; "
                    + "se reintentan en la proxima corrida", resultado.soportesFallidos(), resultado.gruposFallidos());
        }
        log.info("[chat.CompletarChatsDeAprendicesScheduler] soportes creados={} grupos={} chats de dos abiertos={}",
                resultado.soportesCreados(), resultado.gruposRevisados(), resultado.chatsDeDosAbiertos());
    }
}
