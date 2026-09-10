package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.SincronizarParticipantesCelulaUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.AgregarParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.ListarUsuariosDeConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.application.ports.out.participante.QuitarParticipantePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.Participante;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deja los participantes del chat de un grupo iguales a la composición real del grupo.
 *
 * <p>Reconcilia contra la lista completa en vez de aplicar diferencias, y ahí está todo el
 * asunto: el outbox de Modulith entrega at-least-once y sin garantía de orden, así que un
 * evento viejo reentregado después de una rotación volvería a agregar al mentor saliente si la
 * sincronización fuera un delta. Pidiendo la lista entera, el resultado converge al estado de
 * ahora sin importar qué evento la disparó ni cuántas veces (plan.md §6).
 *
 * <p>Lo que NO toca: el {@code conversationId} y los mensajes. Quien sale deja de estar en la
 * lista, pero lo que escribió sigue ahí — la conversación es del grupo, y su historia también.
 */
@Service
public class ParticipantesCelulaService implements SincronizarParticipantesCelulaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ParticipantesCelulaService.class);

    private final LoadConversacionPort loadConversacionPort;
    private final ListarUsuariosDeConversacionPort listarUsuariosPort;
    private final PertenenciaVigentePort pertenenciaVigentePort;
    private final AgregarParticipantePort agregarParticipantePort;
    private final QuitarParticipantePort quitarParticipantePort;
    private final Clock clock;

    public ParticipantesCelulaService(LoadConversacionPort loadConversacionPort,
                                       ListarUsuariosDeConversacionPort listarUsuariosPort,
                                       PertenenciaVigentePort pertenenciaVigentePort,
                                       AgregarParticipantePort agregarParticipantePort,
                                       QuitarParticipantePort quitarParticipantePort, Clock clock) {
        this.loadConversacionPort = loadConversacionPort;
        this.listarUsuariosPort = listarUsuariosPort;
        this.pertenenciaVigentePort = pertenenciaVigentePort;
        this.agregarParticipantePort = agregarParticipantePort;
        this.quitarParticipantePort = quitarParticipantePort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ResultadoSincronizacion sincronizar(UUID celulaId) {
        Optional<Conversacion> quizaConversacion = loadConversacionPort.porCelulaId(celulaId);
        if (quizaConversacion.isEmpty()) {
            // Todavía no tiene chat. No es un error: la conversación se crea por su propio flujo
            // y la próxima corrida la encontrará.
            return new ResultadoSincronizacion(celulaId, 0, 0, true);
        }
        Conversacion conversacion = quizaConversacion.get();

        Set<UserId> deseados = new LinkedHashSet<>(pertenenciaVigentePort.integrantesDelGrupo(celulaId));
        Set<UserId> actuales = new LinkedHashSet<>(listarUsuariosPort.usuariosDe(conversacion.id()));

        int agregados = 0;
        for (UserId deseado : deseados) {
            if (!actuales.contains(deseado)) {
                agregarParticipantePort.agregar(Participante.unirse(conversacion.id(), deseado, clock.now()));
                agregados++;
            }
        }

        int quitados = 0;
        for (UserId actual : actuales) {
            if (!deseados.contains(actual)) {
                quitarParticipantePort.quitar(conversacion.id(), actual);
                quitados++;
            }
        }

        if (agregados > 0 || quitados > 0) {
            log.info("[chat.ParticipantesCelulaService] celula {}: +{} -{} participante(s)",
                    celulaId, agregados, quitados);
        }
        return new ResultadoSincronizacion(celulaId, agregados, quitados, false);
    }

    /** Composición deseada, para diagnóstico. No se usa en la reconciliación. */
    List<UserId> deseadosDe(UUID celulaId) {
        return pertenenciaVigentePort.integrantesDelGrupo(celulaId);
    }
}
