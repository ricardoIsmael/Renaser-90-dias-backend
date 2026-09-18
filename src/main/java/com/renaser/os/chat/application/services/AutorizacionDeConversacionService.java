package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.AutorizarAccesoAConversacionUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa la regla descrita en {@link AutorizarAccesoAConversacionUseCase}.
 *
 * <p>Es la misma decision que toman {@code MensajeService.requireParticipante},
 * {@code ConversacionService} y {@code PresenciaService}, con las mismas dos ramas y en el mismo
 * orden. La diferencia es que aquella lanza y esta responde: el interceptor del WebSocket necesita
 * decidir sin excepciones de dominio, y el reparto de presencia necesita <i>filtrar</i> una lista.
 */
@Service
public class AutorizacionDeConversacionService implements AutorizarAccesoAConversacionUseCase {

    private final LoadConversacionPort loadConversacionPort;
    private final EsParticipantePort esParticipantePort;
    private final PertenenciaVigentePort pertenenciaVigentePort;

    public AutorizacionDeConversacionService(LoadConversacionPort loadConversacionPort,
                                      EsParticipantePort esParticipantePort,
                                      PertenenciaVigentePort pertenenciaVigentePort) {
        this.loadConversacionPort = loadConversacionPort;
        this.esParticipantePort = esParticipantePort;
        this.pertenenciaVigentePort = pertenenciaVigentePort;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean puedeVer(ConversacionId conversacionId, UserId usuarioId) {
        return loadConversacionPort.porId(conversacionId)
                .map(conversacion -> autorizado(conversacion, usuarioId))
                .orElse(false);
    }

    private boolean autorizado(Conversacion conversacion, UserId usuarioId) {
        if (conversacion.tipo() == TipoConversacion.CELULA) {
            /* Para un grupo, la proyeccion `participantes_conversacion` no sirve como fuente: es
               eventual, y una proyeccion vieja no se limita a mostrar de menos — CONCEDE DE MAS.
               Un mentor que roto el mes pasado conservaria su fila, y con ella la puerta abierta
               al chat de gente que ya no acompana. La pertenencia vigente, en cambio, exige que
               el grupo siga operativo y que la asignacion siga viva, y por eso revoca en el acto
               sin depender de que ningun barrido haya corrido. */
            return pertenenciaVigentePort.perteneceAlGrupo(conversacion.celulaId(), usuarioId);
        }
        return esParticipantePort.esParticipante(conversacion.id(), usuarioId);
    }
}
