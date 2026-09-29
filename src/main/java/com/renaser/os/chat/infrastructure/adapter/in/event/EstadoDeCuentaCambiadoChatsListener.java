package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.CompletarChatsDeAprendicesUseCase;
import com.renaser.os.users.api.EstadoDeCuentaCambiadoEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Al reactivarse una cuenta, sus chats al día en el momento (D-224). Una cuenta suspendida al aprobarse
 * el relleno, o al abrirse los chats de su grupo (G-4: solo cuentas activas), se quedaba sin ellos hasta
 * que el grupo cambiara; el barrido horario igual la alcanza, esto solo evita que la persona espere.
 *
 * <p>Solo mira la <i>dirección</i> del cambio (de sin acceso a con acceso), igual que
 * {@code RolDeUsuarioCambiadoSoporteListener}: si corresponde crear algo, y qué, lo decide el caso de uso
 * contra el estado vigente.
 */
@Component
class EstadoDeCuentaCambiadoChatsListener {

    private final CompletarChatsDeAprendicesUseCase completarChats;

    EstadoDeCuentaCambiadoChatsListener(CompletarChatsDeAprendicesUseCase completarChats) {
        this.completarChats = completarChats;
    }

    @ApplicationModuleListener
    void on(EstadoDeCuentaCambiadoEvent event) {
        if (event.estadoNuevo().allowsAccess() && !event.estadoAnterior().allowsAccess()) {
            completarChats.completarDe(event.usuarioId());
        }
    }
}
