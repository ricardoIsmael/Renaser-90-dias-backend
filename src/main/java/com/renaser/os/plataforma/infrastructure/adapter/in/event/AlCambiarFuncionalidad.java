package com.renaser.os.plataforma.infrastructure.adapter.in.event;

import com.renaser.os.plataforma.api.FuncionalidadCambiadaEvent;
import com.renaser.os.plataforma.application.ports.out.AvisarCambioDeEstadosPort;
import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Después del commit de un cambio: esta instancia recarga su copia en el acto —el próximo pedido ya ve el estado
 * nuevo— y avisa a las demás por Redis (≤ 5 s, FR-006). Es síncrono y en el mismo hilo que el {@code PUT}, así que la
 * respuesta sale con la copia ya al día. Repetirlo no hace daño: recargar es idempotente.
 */
@Component
class AlCambiarFuncionalidad {

    private final CopiaDeEstados copia;
    private final AvisarCambioDeEstadosPort aviso;

    AlCambiarFuncionalidad(CopiaDeEstados copia, AvisarCambioDeEstadosPort aviso) {
        this.copia = copia;
        this.aviso = aviso;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(FuncionalidadCambiadaEvent evento) {
        copia.recargar();
        aviso.avisar(copia.foto().version());
    }
}
