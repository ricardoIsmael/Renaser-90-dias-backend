package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.UnirseAConversacionGlobalUseCase;
import com.renaser.os.users.api.UsuarioRegistradoEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Escucha {@link UsuarioRegistradoEvent}, publicado por `users` (`AccountRequestService`
 * al aprobar, `UserAccountService` al invitar) y agrega al usuario a la conversacion
 * GLOBAL — DECISION 2026-08-24 del baseline (V1__baseline_renaser.sql:1293-1295): "todo
 * usuario nuevo se agrega AUTOMATICAMENTE a la conversacion GLOBAL".
 *
 * <p>{@code @ApplicationModuleListener} (no {@code @EventListener} a secas): usa el outbox
 * de Spring Modulith — corre en su propia transaccion, async, DESPUES del commit de la
 * transaccion que registro al usuario (mismo patron que
 * {@code HabitoCompletadoNotificationListener} de `notifications`).
 */
@Component
class UsuarioRegistradoChatListener {

    private final UnirseAConversacionGlobalUseCase unirseUseCase;

    UsuarioRegistradoChatListener(UnirseAConversacionGlobalUseCase unirseUseCase) {
        this.unirseUseCase = unirseUseCase;
    }

    @ApplicationModuleListener
    void on(UsuarioRegistradoEvent event) {
        unirseUseCase.unirse(event.usuarioId());
    }
}
