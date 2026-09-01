package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionCelulaUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionDirectaUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.ListarConversacionesUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.MarcarLeidoUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.RenombrarConversacionGlobalUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.UnirseAConversacionGlobalUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.SaveConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.participante.AgregarParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.ContarNoLeidosPort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.MarcarLeidoPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.Participante;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ConversacionService implements CrearConversacionDirectaUseCase, ListarConversacionesUseCase,
        MarcarLeidoUseCase, UnirseAConversacionGlobalUseCase, CrearConversacionCelulaUseCase,
        RenombrarConversacionGlobalUseCase {

    private final LoadConversacionPort loadConversacionPort;
    private final SaveConversacionPort saveConversacionPort;
    private final AgregarParticipantePort agregarParticipantePort;
    private final EsParticipantePort esParticipantePort;
    private final MarcarLeidoPort marcarLeidoPort;
    private final ContarNoLeidosPort contarNoLeidosPort;
    private final LoadMensajePort loadMensajePort;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public ConversacionService(LoadConversacionPort loadConversacionPort, SaveConversacionPort saveConversacionPort,
                                AgregarParticipantePort agregarParticipantePort,
                                EsParticipantePort esParticipantePort, MarcarLeidoPort marcarLeidoPort,
                                ContarNoLeidosPort contarNoLeidosPort, LoadMensajePort loadMensajePort,
                                UserSummaryFinder userSummaryFinder, Clock clock, IdGenerator idGenerator) {
        this.loadConversacionPort = loadConversacionPort;
        this.saveConversacionPort = saveConversacionPort;
        this.agregarParticipantePort = agregarParticipantePort;
        this.esParticipantePort = esParticipantePort;
        this.marcarLeidoPort = marcarLeidoPort;
        this.contarNoLeidosPort = contarNoLeidosPort;
        this.loadMensajePort = loadMensajePort;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public Conversacion obtenerOCrear(CrearConversacionDirectaCommand command) {
        requireActivo(command.actorId());
        if (command.actorId().equals(command.otroUsuarioId())) {
            throw new IllegalArgumentException("No se puede iniciar una conversacion directa con uno mismo");
        }
        requireActivo(command.otroUsuarioId());

        String clave = Conversacion.claveDirectaDe(command.actorId(), command.otroUsuarioId());
        return loadConversacionPort.porClaveDirecta(clave)
                .orElseGet(() -> crearDirectaConAmbosParticipantes(clave, command.actorId(),
                        command.otroUsuarioId()));
    }

    private Conversacion crearDirectaConAmbosParticipantes(String clave, UserId a, UserId b) {
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD §5.4.7).
        Conversacion guardada = saveConversacionPort.save(
                Conversacion.crearDirecta(ConversacionId.of(idGenerator.newId()), clave, clock.now()));
        agregarParticipantePort.agregar(Participante.unirse(guardada.id(), a, clock.now()));
        agregarParticipantePort.agregar(Participante.unirse(guardada.id(), b, clock.now()));
        return guardada;
    }

    @Override
    public List<ConversacionResumen> listar(UserId actorId) {
        requireActivo(actorId);
        List<Conversacion> conversaciones = loadConversacionPort.misConversaciones(actorId);
        List<ConversacionId> ids = conversaciones.stream().map(Conversacion::id).toList();
        Map<ConversacionId, Mensaje> ultimos = loadMensajePort.ultimosPorConversacion(ids);
        Map<ConversacionId, Long> noLeidos = contarNoLeidosPort.contarNoLeidos(actorId, ids);
        return conversaciones.stream()
                .map(c -> new ConversacionResumen(c, ultimos.get(c.id()), noLeidos.getOrDefault(c.id(), 0L)))
                .sorted(Comparator.comparing(ConversacionService::actividadDe).reversed())
                .toList();
    }

    /** Conversacion sin mensajes: ordena por su fecha de creacion. */
    private static Instant actividadDe(ConversacionResumen resumen) {
        return resumen.ultimoMensaje() != null ? resumen.ultimoMensaje().creadoEn() : resumen.conversacion().creadoEn();
    }

    @Override
    @Transactional
    public void marcarLeido(MarcarLeidoCommand command) {
        requireActivo(command.actorId());
        requireConversacion(command.conversacionId());
        requireParticipante(command.conversacionId(), command.actorId());
        marcarLeidoPort.marcarLeido(command.conversacionId(), command.actorId(), clock.now());
    }

    @Override
    @Transactional
    public void unirse(UserId usuarioId) {
        Conversacion global = loadConversacionPort.global()
                .orElseGet(() -> saveConversacionPort.save(
                        Conversacion.crearGlobal(ConversacionId.of(idGenerator.newId()), clock.now())));
        if (!esParticipantePort.esParticipante(global.id(), usuarioId)) {
            agregarParticipantePort.agregar(Participante.unirse(global.id(), usuarioId, clock.now()));
        }
    }

    @Override
    @Transactional
    public void crearParaCelula(UUID celulaId) {
        if (loadConversacionPort.porCelulaId(celulaId).isPresent()) {
            return;
        }
        saveConversacionPort.save(
                Conversacion.crearCelula(ConversacionId.of(idGenerator.newId()), celulaId, clock.now()));
    }

    @Override
    @Transactional
    public Conversacion renombrar(RenombrarConversacionGlobalCommand command) {
        requireActivoAdmin(command.actorId());
        Conversacion global = loadConversacionPort.global()
                .orElseThrow(() -> new NoSuchElementException("La conversacion GLOBAL todavia no existe"));
        return saveConversacionPort.save(global.renombrada(command.nuevoNombre()));
    }

    private void requireParticipante(ConversacionId conversacionId, UserId usuarioId) {
        if (!esParticipantePort.esParticipante(conversacionId, usuarioId)) {
            throw new NotAuthorizedException("No sos participante de esta conversacion");
        }
    }

    private Conversacion requireConversacion(ConversacionId id) {
        return loadConversacionPort.porId(id)
                .orElseThrow(() -> new NoSuchElementException("Conversacion no encontrada: " + id));
    }

    private void requireActivo(UserId usuarioId) {
        UserSummary usuario = userSummaryFinder.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + usuarioId));
        if (usuario.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }

    /** Igual que {@link #requireActivo} + exige ademas ADMIN/ALCHEMIST
     * ({@code UserRole.canManageRoles()}) — solo lo usa {@link #renombrar}. */
    private void requireActivoAdmin(UserId usuarioId) {
        UserSummary usuario = userSummaryFinder.findById(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + usuarioId));
        if (usuario.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (!usuario.role().canManageRoles()) {
            throw new NotAuthorizedException("Solo ADMIN/ALCHEMIST puede renombrar el chat global");
        }
    }
}
