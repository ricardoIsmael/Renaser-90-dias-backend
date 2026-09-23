package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort.HabitoDelDia;
import com.renaser.os.rag.application.ports.out.ia.ClasificarIntencionPort;
import com.renaser.os.rag.domain.model.intencion.IntencionClasificada;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * El default ({@code renaser.ia.router.proveedor=noop}): no opina, y el acompanante se comporta
 * exactamente como antes de que existiera el router. Un fallo o una configuracion ausente nunca
 * cambian la conversacion.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.router.proveedor", havingValue = "noop", matchIfMissing = true)
class NoOpClasificarIntencionAdapter implements ClasificarIntencionPort {

    @Override
    public IntencionClasificada clasificar(String mensaje, List<HabitoDelDia> habitosDeHoy) {
        return IntencionClasificada.sinClasificar();
    }
}
