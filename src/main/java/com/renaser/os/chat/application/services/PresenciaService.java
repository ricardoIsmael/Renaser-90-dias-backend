package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.presencia.ConsultarPresenciaUseCase;
import com.renaser.os.chat.application.ports.in.presencia.RegistrarPresenciaUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.ConversacionesDeUsuarioPort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.ListarUsuariosDeConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.application.ports.out.presencia.PresenciaPort;
import com.renaser.os.chat.application.ports.out.presencia.PublicarPresenciaFanoutPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.domain.NotAuthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Quien esta conectado ahora mismo, para el indicador "en linea" del chat.
 *
 * <p>Hasta 2026-09-17 la app mostraba un punto verde y la palabra "En linea" <b>escritos a
 * mano</b> debajo del nombre de cualquier persona: afirmaba algo que el sistema no sabia, y no
 * habia forma de que fuera cierto ni por casualidad. Este servicio es el dato de verdad.
 *
 * <p><b>Presencia es estado efimero, no dominio.</b> Vive en Redis con vencimiento (ver
 * {@link PresenciaPort}) y no toca Postgres. Un backend que se cae deja de refrescar y sus
 * usuarios se apagan solos al vencer la llave — preferible a una tabla que quede afirmando
 * que todos siguen conectados.
 *
 * <p><b>Nunca falla hacia arriba.</b> Conectarse a un chat no puede romperse porque Redis no
 * responda: si el registro de presencia falla, se loguea y se sigue. Lo peor que pasa es que
 * el indicador no se encienda; el chat funciona igual, como funcionaba antes de que esto
 * existiera.
 */
@Service
public class PresenciaService implements ConsultarPresenciaUseCase, RegistrarPresenciaUseCase {

    private static final Logger log = LoggerFactory.getLogger(PresenciaService.class);

    /**
     * Cuanto vale una marca de presencia sin que nadie la refresque.
     *
     * <p>Cuatro veces el periodo de refresco del adaptador (45 s): asi hace falta perder tres
     * refrescos seguidos para que alguien conectado parpadee a "ausente". Y al reves, una
     * instancia que muere sin avisar deja a su gente marcada como presente a lo sumo estos
     * tres minutos, no para siempre.
     */
    public static final Duration VIGENCIA = Duration.ofMinutes(3);

    private final PresenciaPort presenciaPort;
    private final PublicarPresenciaFanoutPort publicarPresenciaFanoutPort;
    private final ConversacionesDeUsuarioPort conversacionesDeUsuarioPort;
    private final ListarUsuariosDeConversacionPort listarUsuariosDeConversacionPort;
    private final LoadConversacionPort loadConversacionPort;
    private final EsParticipantePort esParticipantePort;
    private final PertenenciaVigentePort pertenenciaVigentePort;

    public PresenciaService(PresenciaPort presenciaPort,
                            PublicarPresenciaFanoutPort publicarPresenciaFanoutPort,
                            ConversacionesDeUsuarioPort conversacionesDeUsuarioPort,
                            ListarUsuariosDeConversacionPort listarUsuariosDeConversacionPort,
                            LoadConversacionPort loadConversacionPort,
                            EsParticipantePort esParticipantePort,
                            PertenenciaVigentePort pertenenciaVigentePort) {
        this.presenciaPort = presenciaPort;
        this.publicarPresenciaFanoutPort = publicarPresenciaFanoutPort;
        this.conversacionesDeUsuarioPort = conversacionesDeUsuarioPort;
        this.listarUsuariosDeConversacionPort = listarUsuariosDeConversacionPort;
        this.loadConversacionPort = loadConversacionPort;
        this.esParticipantePort = esParticipantePort;
        this.pertenenciaVigentePort = pertenenciaVigentePort;
    }

    @Override
    public Set<UserId> enLineaEn(ConversacionId conversacionId, UserId actorId) {
        requireParticipante(requireConversacion(conversacionId), actorId);
        List<UserId> otros = listarUsuariosDeConversacionPort.usuariosDe(conversacionId).stream()
                .filter(id -> !id.equals(actorId))
                .toList();
        if (otros.isEmpty()) {
            return Set.of();
        }
        try {
            return presenciaPort.enLineaDe(otros);
        } catch (RuntimeException redisCaido) {
            // "No se sabe" se responde como "nadie en linea", que es la lectura prudente: no
            // afirma una presencia que no se pudo comprobar. Es exactamente lo contrario del
            // texto fijo que esto vino a reemplazar.
            log.warn("No se pudo leer la presencia de la conversacion {}", conversacionId, redisCaido);
            return Set.of();
        }
    }

    @Override
    public void seConecto(UserId usuarioId) {
        cambiarPresencia(usuarioId, true);
    }

    @Override
    public void seDesconecto(UserId usuarioId) {
        cambiarPresencia(usuarioId, false);
    }

    @Override
    public void sigueConectado(UserId usuarioId) {
        // Solo renueva el vencimiento: no hay cambio que avisar, y publicar "sigue en linea"
        // cada 45 segundos a cada conversacion seria ruido puro en todos los sockets abiertos.
        try {
            presenciaPort.marcarEnLinea(usuarioId, VIGENCIA);
        } catch (RuntimeException e) {
            log.warn("No se pudo renovar la presencia de {}", usuarioId, e);
        }
    }

    private void cambiarPresencia(UserId usuarioId, boolean enLinea) {
        try {
            if (enLinea) {
                presenciaPort.marcarEnLinea(usuarioId, VIGENCIA);
            } else {
                presenciaPort.marcarFueraDeLinea(usuarioId);
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar la presencia de {} (enLinea={})", usuarioId, enLinea, e);
            return;
        }
        try {
            List<ConversacionId> destinos = conversacionesDeUsuarioPort.conversacionesDe(usuarioId)
                    .stream().filter(destino -> puedeVerAhora(destino, usuarioId)).toList();
            if (!destinos.isEmpty()) {
                publicarPresenciaFanoutPort.publicar(usuarioId, enLinea, destinos);
            }
        } catch (RuntimeException e) {
            // El estado ya quedo escrito; lo unico que se pierde es el aviso inmediato. Quien
            // abra la conversacion despues lo leera igual con `enLineaEn`.
            log.warn("No se pudo avisar el cambio de presencia de {}", usuarioId, e);
        }
    }

    /**
     * La MISMA autorizacion que para leer los mensajes de esa conversacion (ver
     * {@code MensajeService.requireParticipante}): para un grupo no alcanza la proyeccion
     * {@code participantes_conversacion}, porque una proyeccion vieja concede de mas — un
     * mentor que roto conservaria su fila y con ella la lista de quien esta conectado en un
     * grupo que ya no acompana. Saber quien esta en linea es menos que leer lo que escriben,
     * pero es informacion del mismo grupo y se cuida igual.
     */
    private void requireParticipante(Conversacion conversacion, UserId usuarioId) {
        if (autorizado(conversacion, usuarioId)) {
            return;
        }
        throw new NotAuthorizedException(conversacion.tipo() == TipoConversacion.CELULA
                ? "Tu asignacion cambio: ya no perteneces a ese grupo"
                : "No sos participante de esta conversacion");
    }

    private boolean autorizado(Conversacion conversacion, UserId usuarioId) {
        return conversacion.tipo() == TipoConversacion.CELULA
                ? pertenenciaVigentePort.perteneceAlGrupo(conversacion.celulaId(), usuarioId)
                : esParticipantePort.esParticipante(conversacion.id(), usuarioId);
    }

    /**
     * La MISMA pregunta que {@link #requireParticipante}, para poder FILTRAR en vez de lanzar.
     *
     * <p>El aviso de presencia se repartia a {@code conversacionesDe(usuarioId)} tal cual, o sea a
     * la proyeccion. Leer quien esta en linea si revalidaba la pertenencia vigente, pero
     * ANUNCIARLO no: el estado de conexion de una persona se publicaba al topic de todo grupo
     * donde conservara fila, incluidos los que ya no integra. Para un grupo cuyo periodo termino
     * eso no se corrige solo nunca, porque el fin de periodo no publica
     * {@code ComposicionDeCelulaCambiadaEvent} y la proyeccion no se vuelve a tocar.
     */
    private boolean puedeVerAhora(ConversacionId conversacionId, UserId usuarioId) {
        return loadConversacionPort.porId(conversacionId)
                .map(conversacion -> autorizado(conversacion, usuarioId))
                .orElse(false);
    }

    private Conversacion requireConversacion(ConversacionId id) {
        return loadConversacionPort.porId(id)
                .orElseThrow(() -> new NoSuchElementException("Conversacion no encontrada: " + id));
    }
}
