package com.renaser.os.leadership.application.services;

import com.renaser.os.chat.api.MensajeDirectoPort;
import com.renaser.os.chat.api.MensajeDirectoPort.MensajeDirecto;
import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.leadership.application.ports.in.ConsultarObservacionesUseCase;
import com.renaser.os.leadership.application.ports.in.RegistrarObservacionUseCase;
import com.renaser.os.leadership.application.ports.out.GuardarObservacionPort;
import com.renaser.os.leadership.application.ports.out.LeerObservacionesPort;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor.Contenido;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor.Envio;
import com.renaser.os.leadership.domain.model.observacion.ObservacionDeMentor.SobrePersona;
import com.renaser.os.leadership.domain.model.observacion.TipoObservacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registrar y leer las observaciones del líder sobre un mentor (SDD 002, RL-15..RL-18; D-241).
 * Append-only: no hay un caso de uso que las edite ni las borre.
 *
 * <p>Spec 005 (R-7): «Avisar a su mentor» desde la ficha de una persona es esta misma observación, con la persona y la
 * semana. La persona tiene que estar HOY en un grupo que ese mentor acompaña; si no, 400.
 *
 * <p><b>Spec 056 (FR-025, SC-005): el servidor se la entrega al mentor.</b> Al guardarla le escribe el texto por el chat directo del líder con el mentor
 * ({@link MensajeDirectoPort}) y publica el aviso para su teléfono ({@link AvisosDeObservaciones}). Todo en la misma transacción, y repetir la
 * {@code claveOperacion} devuelve lo ya creado sin reenviar nada, así que llega una sola vez: dos pedidos a la vez chocan contra el índice único y el que
 * pierde deshace también su mensaje. Si el pedido trae {@code messageId} es una app vieja que ya mandó el mensaje: no se reenvía, y el aviso sale igual.
 *
 * <p><b>RL-16 deja de aplicar a lo que se envía desde el servidor</b> (SC-005: el 100 % de las observaciones llegan al mentor como mensaje y como aviso).
 * Antes la observación se guardaba aunque el chat fallara, porque la app mandaba el mensaje por su cuenta y podía fallar a medias. Ahora, si el chat no puede
 * entregarlo (los chats directos apagados en esa formación, dos personas sin formación en común, una cuenta suspendida), el error sube con su motivo y no
 * queda nada guardado: el líder sabe que el mentor no se enteró y conserva su texto para reintentar. Sigue valiendo para una app vieja que ya mandó el
 * mensaje (trae {@code messageId}).
 *
 * <blockquote><b>Corregido 2026-11-18 (spec 056).</b> Esta clase decía «no manda nada solo»: el mensaje por el chat lo mandaba la app antes de pedir
 * este registro, y si fallaba a medias el mentor no se enteraba de nada.</blockquote>
 */
@Service
public class ObservacionesDeMentorService implements RegistrarObservacionUseCase, ConsultarObservacionesUseCase {

    static final int TAMANO_PAGINA = 20;

    private final AccesoDeLiderazgo acceso;
    private final GuardarObservacionPort guardar;
    private final LeerObservacionesPort leer;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final UserSummaryFinder usuarios;
    private final AcompanamientoFinder acompanamiento;
    private final MensajeDirectoPort chat;
    private final AvisosDeObservaciones avisos;

    ObservacionesDeMentorService(AccesoDeLiderazgo acceso, GuardarObservacionPort guardar,
                                 LeerObservacionesPort leer, IdGenerator idGenerator, Clock clock,
                                 UserSummaryFinder usuarios, AcompanamientoFinder acompanamiento,
                                 MensajeDirectoPort chat, AvisosDeObservaciones avisos) {
        this.acceso = acceso;
        this.guardar = guardar;
        this.leer = leer;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.usuarios = usuarios;
        this.acompanamiento = acompanamiento;
        this.chat = chat;
        this.avisos = avisos;
    }

    @Override
    @Transactional
    public ObservacionDeMentor registrar(RegistrarObservacionCommand comando) {
        acceso.requireLiderazgoActivo(comando.actorId());
        acceso.requireMentor(comando.mentorId());
        Optional<ObservacionDeMentor> yaRegistrada = leer.porAutorYClave(comando.actorId(), comando.claveOperacion());
        if (yaRegistrada.isPresent()) {
            return mismaOperacion(yaRegistrada.get(), comando);
        }
        SobrePersona sobre = sobre(comando);
        MensajeDirecto entregado = entregarPorElChat(comando);
        Envio envio = envioDe(comando, entregado);
        Contenido contenido = new Contenido(comando.mentorId(), comando.actorId(), TipoObservacion.de(comando.tipo()),
                comando.texto(), envio, comando.claveOperacion(), sobre);
        ObservacionDeMentor guardada = guardar.insertar(ObservacionDeMentor.registrar(idGenerator.newId(), contenido, clock));
        avisos.alMentor(guardada, entregado == null ? null : entregado.conversacionId());
        return guardada;
    }

    /**
     * El texto al mentor por el chat de dos del líder con él (spec 056, FR-025). {@code null} si el pedido ya trae el mensaje: una app vieja lo mandó antes y
     * reenviarlo lo duplicaría en el chat. Va ANTES de insertar para que el id del mensaje quede en la observación, y dentro de la misma transacción: el
     * mensaje de un pedido que pierde la carrera contra el índice único se deshace con él, y un rechazo del chat deshace todo con su motivo.
     */
    private MensajeDirecto entregarPorElChat(RegistrarObservacionCommand comando) {
        if (comando.mensajeId() != null) {
            return null;
        }
        String texto = comando.texto() == null ? null : comando.texto().trim();
        return chat.enviar(comando.actorId(), comando.mentorId(), texto);
    }

    /** Lo que la observación dice de su envío: el mensaje que salió ahora, o el que ya mandó la app. */
    private static Envio envioDe(RegistrarObservacionCommand comando, MensajeDirecto entregado) {
        return entregado != null ? new Envio(true, entregado.mensajeId()) : new Envio(comando.enviadaPorChat(), comando.mensajeId());
    }

    /** La persona de la que trata, si trata de alguien: tiene que ser hoy de un grupo de ESE mentor. */
    private SobrePersona sobre(RegistrarObservacionCommand comando) {
        if (comando.personaId() == null) {
            return null;
        }
        Instant ahora = clock.now();
        boolean esDeSuGrupo = acompanamiento.gruposConMentorVigente(ahora).stream()
                .filter(grupo -> comando.mentorId().equals(grupo.mentorId()))
                .anyMatch(grupo -> acompanamiento.aprendicesVigentes(grupo.grupoId(), ahora)
                        .contains(comando.personaId()));
        if (!esDeSuGrupo) {
            throw new IllegalArgumentException("Esa persona no esta en un grupo que ese mentor acompana");
        }
        return new SobrePersona(comando.personaId(), comando.semanaDesde());
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDeObservaciones observaciones(UserId actorId, UserId mentorId, Instant antesDe) {
        acceso.requireLiderazgoActivo(actorId);
        acceso.requireMentor(mentorId);
        List<ObservacionDeMentor> filas = leer.deMentor(mentorId, antesDe, TAMANO_PAGINA + 1);
        boolean hayMas = filas.size() > TAMANO_PAGINA;
        List<ObservacionDeMentor> pagina = hayMas ? filas.subList(0, TAMANO_PAGINA) : filas;
        return new PaginaDeObservaciones(pagina, hayMas ? pagina.getLast().creadoEn() : null, nombresDe(pagina));
    }

    /** Una sola lectura de cuentas para toda la página, nunca una por observación. */
    private Map<UserId, String> nombresDe(List<ObservacionDeMentor> pagina) {
        List<UserId> personas = pagina.stream().map(ObservacionDeMentor::personaId).filter(Objects::nonNull)
                .distinct().toList();
        Map<UserId, String> nombres = new HashMap<>();
        if (!personas.isEmpty()) {
            usuarios.findByIds(personas).forEach((id, perfil) -> nombres.put(id, perfil.fullName()));
        }
        return nombres;
    }

    /** Un reintento devuelve lo ya creado; la misma clave para OTRO mentor es un error del cliente. */
    private static ObservacionDeMentor mismaOperacion(ObservacionDeMentor existente, RegistrarObservacionCommand comando) {
        if (!existente.mentorId().equals(comando.mentorId())
                || !Objects.equals(existente.personaId(), comando.personaId())) {
            throw new IllegalArgumentException("Esa clave de operación ya se usó para otra observación");
        }
        return existente;
    }
}
