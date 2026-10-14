package com.renaser.os.plataforma.infrastructure.adapter.in.event;

import com.renaser.os.plataforma.application.ports.out.AvisarCambioDeRolesPersonalizadosPort;
import com.renaser.os.plataforma.application.services.CopiaDeRolesPersonalizados;
import com.renaser.os.users.api.RolPersonalizadoCambiadoEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Después del commit de un cambio de rol personalizado o de una asignación (D-269): esta instancia recarga su copia en
 * el acto —el próximo pedido de esa persona ya ve su rol nuevo— y avisa a las demás por Redis. Síncrono y en el mismo
 * hilo, como {@link AlCambiarFuncionalidad}: la respuesta del pedido que cambió el rol sale con la copia ya al día.
 * Recargar es idempotente.
 */
@Component
class AlCambiarRolPersonalizado {

    private final CopiaDeRolesPersonalizados copia;
    private final AvisarCambioDeRolesPersonalizadosPort aviso;

    AlCambiarRolPersonalizado(CopiaDeRolesPersonalizados copia, AvisarCambioDeRolesPersonalizadosPort aviso) {
        this.copia = copia;
        this.aviso = aviso;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(RolPersonalizadoCambiadoEvent evento) {
        copia.recargar();
        aviso.avisar();
    }
}
