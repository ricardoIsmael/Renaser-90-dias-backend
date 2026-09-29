package com.renaser.os.chat.application.services;

import com.renaser.os.chat.api.AvisosDeMensajesFinder;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.participante.ContarNoLeidosPort;
import com.renaser.os.chat.application.ports.out.participante.ListarUsuariosDeConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.application.ports.out.presencia.ConversacionAbiertaPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.NombreDelChat;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A quién avisar de un mensaje nuevo y con qué nombre (D-221). Lo pregunta {@code notifications} al
 * entregar el evento {@code MensajeDeChatGuardadoEvent}.
 *
 * <p><b>Quién recibe, con la misma regla que decide quién VE el chat</b> (la de
 * {@code AutorizacionDeConversacionService}, resuelta en lote porque la comunidad son cientos):
 * <ul>
 *   <li>comunidad y 1 a 1: los participantes;</li>
 *   <li>grupo: los participantes que pertenecen HOY al grupo (la proyección concede de más a quien
 *       rotó: un exmentor no recibe los mensajes de sus exalumnos);</li>
 *   <li>soporte: el aprendiz y quienes son ADMIN/ALCHEMIST ahora (una baja de rol corta el aviso aunque
 *       la revocación todavía no haya borrado su fila);</li>
 * </ul>
 * y en todos, menos el autor (un mensaje del programa no tiene autor: llega a todos, también a la
 * persona de quien habla, que es a quien se le da la bienvenida) y menos quien tiene el chat abierto.
 *
 * <p>Solo lee: sin {@code @Transactional}, cada consulta en la suya. Nada acá llama afuera del proceso
 * salvo Redis (quién tiene el chat abierto).
 */
@Service
public class AvisosDeMensajesService implements AvisosDeMensajesFinder {

    private final LoadMensajePort loadMensajePort;
    private final LoadConversacionPort loadConversacionPort;
    private final ListarUsuariosDeConversacionPort listarUsuariosPort;
    private final PertenenciaVigentePort pertenenciaVigentePort;
    private final ContarNoLeidosPort contarNoLeidosPort;
    private final ConversacionAbiertaPort conversacionAbiertaPort;
    private final UserSummaryFinder userSummaryFinder;
    private final NombresDeLosChatsService nombresDeLosChats;

    public AvisosDeMensajesService(LoadMensajePort loadMensajePort, LoadConversacionPort loadConversacionPort,
                                   ListarUsuariosDeConversacionPort listarUsuariosPort,
                                   PertenenciaVigentePort pertenenciaVigentePort, ContarNoLeidosPort contarNoLeidosPort,
                                   ConversacionAbiertaPort conversacionAbiertaPort, UserSummaryFinder userSummaryFinder,
                                   NombresDeLosChatsService nombresDeLosChats) {
        this.loadMensajePort = loadMensajePort;
        this.loadConversacionPort = loadConversacionPort;
        this.listarUsuariosPort = listarUsuariosPort;
        this.pertenenciaVigentePort = pertenenciaVigentePort;
        this.contarNoLeidosPort = contarNoLeidosPort;
        this.conversacionAbiertaPort = conversacionAbiertaPort;
        this.userSummaryFinder = userSummaryFinder;
        this.nombresDeLosChats = nombresDeLosChats;
    }

    @Override
    public Optional<AvisoDeMensaje> avisoDe(UUID mensajeId) {
        return loadMensajePort.porId(MensajeId.of(mensajeId))
                .flatMap(mensaje -> loadConversacionPort.porId(mensaje.conversacionId())
                        .map(conversacion -> aviso(mensaje, conversacion)));
    }

    private AvisoDeMensaje aviso(Mensaje mensaje, Conversacion conversacion) {
        String autor = autorDe(mensaje);
        boolean unoAUno = conversacion.tipo() == TipoConversacion.DIRECTA;
        String nombreDelChat = unoAUno ? autor : nombresDeLosChats.nombreDe(conversacion);
        List<UserId> destinatarios = destinatarios(mensaje, conversacion);
        Map<UserId, Long> sinLeer = contarNoLeidosPort.noLeidosPorParticipante(conversacion.id(), destinatarios);
        return new AvisoDeMensaje(conversacion.id().value(), rutaDe(conversacion), unoAUno, autor,
                contenidoDe(mensaje), textoDe(mensaje),
                destinatarios.stream()
                        .map(d -> new Destinatario(d, nombreDelChat, Math.max(1L, sinLeer.getOrDefault(d, 1L))))
                        .toList());
    }

    /** La ruta que abre el chat en la app (D-218 para las demás): {@code /chat/{id}}. */
    static String rutaDe(Conversacion conversacion) {
        return "/chat/" + conversacion.id().value();
    }

    private List<UserId> destinatarios(Mensaje mensaje, Conversacion conversacion) {
        List<UserId> candidatos = listarUsuariosPort.usuariosDe(conversacion.id()).stream()
                .filter(u -> !mensaje.escritoPor(u))
                .distinct()
                .toList();
        List<UserId> quePuedenVer = quePuedenVer(conversacion, candidatos);
        Set<UserId> conElChatAbierto = conversacionAbiertaPort.laTienenAbierta(conversacion.id(), quePuedenVer);
        return quePuedenVer.stream().filter(u -> !conElChatAbierto.contains(u)).toList();
    }

    private List<UserId> quePuedenVer(Conversacion conversacion, List<UserId> candidatos) {
        if (candidatos.isEmpty()) {
            return candidatos;
        }
        return switch (conversacion.tipo()) {
            case GLOBAL, DIRECTA -> candidatos;
            case CELULA -> {
                Set<UserId> vigentes = new HashSet<>(pertenenciaVigentePort.integrantesDelGrupo(conversacion.celulaId()));
                yield candidatos.stream().filter(vigentes::contains).toList();
            }
            case SOPORTE -> {
                Map<UserId, UserSummary> perfiles = userSummaryFinder.findByIds(candidatos);
                yield candidatos.stream()
                        .filter(u -> conversacion.esAprendizDeSoporte(u) || esStaffAhora(perfiles.get(u)))
                        .toList();
            }
        };
    }

    /** El mismo conjunto que {@code ConversacionSoporteService.STAFF_ADMINISTRATIVO}. Sin perfil, no. */
    private static boolean esStaffAhora(UserSummary perfil) {
        return perfil != null && perfil.role().canManageRoles();
    }

    private String autorDe(Mensaje mensaje) {
        if (mensaje.esDelPrograma()) {
            return NombreDelChat.FORMACION_RENASER;
        }
        return userSummaryFinder.findById(mensaje.emisorId())
                .map(UserSummary::fullName)
                .filter(nombre -> !nombre.isBlank())
                .orElse("Miembro Renaser");
    }

    private static Contenido contenidoDe(Mensaje mensaje) {
        return switch (mensaje.tipo()) {
            case IMAGEN -> Contenido.FOTO;
            case AUDIO -> Contenido.NOTA_DE_VOZ;
            case VIDEO -> Contenido.VIDEO;
            // Uno del programa sin texto es la tarjeta de bienvenida sola: una foto.
            case SISTEMA -> tieneTexto(mensaje) || mensaje.mediaRuta() == null ? Contenido.TEXTO : Contenido.FOTO;
            case TEXTO -> Contenido.TEXTO;
        };
    }

    private static String textoDe(Mensaje mensaje) {
        return tieneTexto(mensaje) ? mensaje.texto().strip() : null;
    }

    private static boolean tieneTexto(Mensaje mensaje) {
        return mensaje.texto() != null && !mensaje.texto().isBlank();
    }
}
