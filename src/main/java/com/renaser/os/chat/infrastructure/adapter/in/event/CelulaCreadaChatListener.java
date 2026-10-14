package com.renaser.os.chat.infrastructure.adapter.in.event;

import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionCelulaUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.SincronizarParticipantesCelulaUseCase;
import com.renaser.os.community.api.CelulaCreadaEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Escucha {@link CelulaCreadaEvent}, publicado por `community` (`CelulaService.crear`) y
 * crea la conversacion CELULA correspondiente, de forma idempotente — protegida ademas
 * por {@code conversaciones.celula_id UNIQUE} (V1__baseline_renaser.sql:1281).
 *
 * <p>Despues de crearla reconcilia los participantes, como el aviso de composicion
 * ({@code ComposicionCelulaChatListener}). <b>Corregido 2026-10-14 (E-625).</b> Antes solo creaba la
 * fila: si el grupo nacia con integrantes en el mismo commit (el grupo de prueba de «ver como»,
 * D-270), los dos avisos salian juntos del outbox, el de la composicion podia llegar primero, no
 * encontraba la conversacion y no hacia nada, y nadie volvia a sincronizar. La sincronizacion es
 * idempotente: en un grupo recien creado sin integrantes no cambia nada.
 */
@Component
class CelulaCreadaChatListener {

    private final CrearConversacionCelulaUseCase crearConversacionCelulaUseCase;
    private final SincronizarParticipantesCelulaUseCase sincronizarParticipantes;

    CelulaCreadaChatListener(CrearConversacionCelulaUseCase crearConversacionCelulaUseCase,
                             SincronizarParticipantesCelulaUseCase sincronizarParticipantes) {
        this.crearConversacionCelulaUseCase = crearConversacionCelulaUseCase;
        this.sincronizarParticipantes = sincronizarParticipantes;
    }

    @ApplicationModuleListener
    void on(CelulaCreadaEvent event) {
        crearConversacionCelulaUseCase.crearParaCelula(event.celulaId());
        sincronizarParticipantes.sincronizar(event.celulaId());
    }
}
