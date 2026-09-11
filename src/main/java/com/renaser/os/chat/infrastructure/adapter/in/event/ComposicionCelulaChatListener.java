package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.SincronizarParticipantesCelulaUseCase;
import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reconcilia los participantes del chat cuando la composición del grupo cambia.
 *
 * <p>Completa lo que {@code CelulaCreadaChatListener} dejaba explícitamente afuera: aquel crea
 * la conversación, este mantiene su lista. Hasta ahora nadie lo hacía, y por eso la proyección
 * de participantes de un grupo podía quedar congelada para siempre.
 *
 * <p>{@code @ApplicationModuleListener} corre en su propia transacción después del commit, así
 * que un fallo acá no puede deshacer la rotación que lo originó. La reentrega tampoco es
 * problema: la sincronización reconcilia contra la lista completa, no aplica diferencias.
 */
@Component
class ComposicionCelulaChatListener {

    private final SincronizarParticipantesCelulaUseCase sincronizarParticipantes;

    ComposicionCelulaChatListener(SincronizarParticipantesCelulaUseCase sincronizarParticipantes) {
        this.sincronizarParticipantes = sincronizarParticipantes;
    }

    @ApplicationModuleListener
    void on(ComposicionDeCelulaCambiadaEvent event) {
        sincronizarParticipantes.sincronizar(event.celulaId());
    }
}
