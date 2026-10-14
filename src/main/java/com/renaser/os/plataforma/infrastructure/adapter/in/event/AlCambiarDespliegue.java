package com.renaser.os.plataforma.infrastructure.adapter.in.event;

import com.renaser.os.plataforma.application.ports.out.AvisarCambioDeEstadosPort;
import com.renaser.os.plataforma.application.services.CopiaDeDespliegues;
import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import com.renaser.os.plataforma.domain.model.despliegue.DespliegueCambiado;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Después del commit de un cambio de despliegue (escalón o probadores): esta instancia recarga su copia en el acto —la
 * respuesta del {@code PUT} sale con la regla ya al día— y avisa a las demás por el mismo canal Redis de los
 * interruptores (contrato fase 2 §3.2).
 *
 * <p>Manda la versión de la copia de INTERRUPTORES que ya tiene, no un número del despliegue: así el oyente de la fase 1
 * de cada instancia la descarta (no es más nueva que la suya) y no recarga de más, mientras que el oyente de despliegues
 * recarga con cualquier aviso. El formato del mensaje no cambia (sigue siendo un número), así que el oyente de la fase 1
 * no anota avisos «malformados».
 */
@Component
class AlCambiarDespliegue {

    private final CopiaDeDespliegues despliegues;
    private final CopiaDeEstados estados;
    private final AvisarCambioDeEstadosPort aviso;

    AlCambiarDespliegue(CopiaDeDespliegues despliegues, CopiaDeEstados estados, AvisarCambioDeEstadosPort aviso) {
        this.despliegues = despliegues;
        this.estados = estados;
        this.aviso = aviso;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(DespliegueCambiado evento) {
        despliegues.recargar();
        aviso.avisar(estados.foto().version());
    }
}
