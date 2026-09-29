package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.AutorizarAccesoAConversacionUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotosDelChatUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerParticipantesDeConversacionUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.MentorDeLosGruposPort;
import com.renaser.os.chat.application.ports.out.participante.ListarUsuariosDeConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.RolEnElChat;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Los integrantes de una conversación (ver {@link VerParticipantesDeConversacionUseCase}).
 *
 * <p><b>El orden de las comprobaciones</b> es el de {@code FotosDelChatService}: cuenta activa (403), la
 * conversación existe (404) y quien pide puede verla (403). A quien no la ve no se le dice quién está adentro.
 *
 * <p>La lista de un grupo sale de la pertenencia vigente y no de la proyección {@code participantes_conversacion}
 * (que concede de más a quien rotó); la de un soporte, del aprendiz más el staff de AHORA. Nombre, rol y
 * estado se resuelven en UN lote contra {@code users.api}; la búsqueda y la paginación son en memoria porque
 * la tabla de usuarios es de otro módulo (mismo criterio que {@code MiembroService}).
 *
 * <p>Solo lee: sin {@code @Transactional}, cada consulta en la suya.
 */
@Service
public class ParticipantesDeConversacionService implements VerParticipantesDeConversacionUseCase {

    private static final int TAMANO_MAXIMO = 200;

    private final LoadConversacionPort loadConversacionPort;
    private final AutorizarAccesoAConversacionUseCase autorizarAcceso;
    private final ListarUsuariosDeConversacionPort listarUsuariosPort;
    private final PertenenciaVigentePort pertenenciaVigentePort;
    private final MentorDeLosGruposPort mentorDeLosGrupos;
    private final UserSummaryFinder userSummaryFinder;
    private final VerFotosDelChatUseCase fotos;

    public ParticipantesDeConversacionService(LoadConversacionPort loadConversacionPort,
                                              AutorizarAccesoAConversacionUseCase autorizarAcceso,
                                              ListarUsuariosDeConversacionPort listarUsuariosPort,
                                              PertenenciaVigentePort pertenenciaVigentePort,
                                              MentorDeLosGruposPort mentorDeLosGrupos,
                                              UserSummaryFinder userSummaryFinder, VerFotosDelChatUseCase fotos) {
        this.loadConversacionPort = loadConversacionPort;
        this.autorizarAcceso = autorizarAcceso;
        this.listarUsuariosPort = listarUsuariosPort;
        this.pertenenciaVigentePort = pertenenciaVigentePort;
        this.mentorDeLosGrupos = mentorDeLosGrupos;
        this.userSummaryFinder = userSummaryFinder;
        this.fotos = fotos;
    }

    @Override
    public PaginaDeParticipantes ver(UserId actorId, ConversacionId conversacionId, String consulta, int pagina,
                                     int tamano) {
        requireActivo(actorId);
        Conversacion conversacion = loadConversacionPort.porId(conversacionId)
                .orElseThrow(() -> new NoSuchElementException("Conversacion no encontrada: " + conversacionId));
        if (!autorizarAcceso.puedeVer(conversacionId, actorId)) {
            throw new NotAuthorizedException("No eres participante de esta conversación");
        }
        UserId mentor = mentorVigenteDe(conversacion);
        List<UserSummary> ordenados = integrantesDe(conversacion).stream()
                .filter(u -> coincide(u, consulta))
                .sorted(orden(conversacion, mentor))
                .toList();
        int tamanoEfectivo = Math.clamp(tamano, 1, TAMANO_MAXIMO);
        int paginaEfectiva = Math.max(pagina, 0);
        int desde = (int) Math.min((long) paginaEfectiva * tamanoEfectivo, ordenados.size());
        List<UserSummary> lote = ordenados.subList(desde, Math.min(desde + tamanoEfectivo, ordenados.size()));
        return new PaginaDeParticipantes(filas(conversacion, lote, mentor, actorId), ordenados.size(),
                paginaEfectiva, tamanoEfectivo);
    }

    private List<ParticipanteDelChat> filas(Conversacion conversacion, List<UserSummary> lote, UserId mentor,
                                            UserId actorId) {
        Set<UserId> conTarjeta = fotos.conTarjetaEn(conversacion.tipo(), lote.stream().map(UserSummary::id).toList());
        return lote.stream()
                .map(u -> new ParticipanteDelChat(u.id(), nombreDe(u), rolDe(u, mentor), u.id().equals(actorId),
                        conTarjeta.contains(u.id()), u.avatarUrl()))
                .toList();
    }

    /** Quienes son integrantes HOY de esa conversación, activos, con su perfil resuelto en un lote. */
    private List<UserSummary> integrantesDe(Conversacion conversacion) {
        Map<UserId, UserSummary> perfiles = userSummaryFinder.findByIds(candidatosDe(conversacion));
        return perfiles.values().stream()
                .filter(u -> u.status() == UserStatus.ACTIVE)
                .filter(u -> pertenece(conversacion, u))
                .toList();
    }

    private Set<UserId> candidatosDe(Conversacion conversacion) {
        Set<UserId> candidatos = new LinkedHashSet<>();
        switch (conversacion.tipo()) {
            case CELULA -> candidatos.addAll(pertenenciaVigentePort.integrantesDelGrupo(conversacion.celulaId()));
            case SOPORTE -> {
                conversacion.aprendizDelSoporte().ifPresent(candidatos::add);
                candidatos.addAll(listarUsuariosPort.usuariosDe(conversacion.id()));
            }
            case GLOBAL, DIRECTA -> candidatos.addAll(listarUsuariosPort.usuariosDe(conversacion.id()));
        }
        return candidatos;
    }

    /** Un soporte es del aprendiz y del staff de AHORA: la fila sola no alcanza (mismo criterio que la autorización). */
    private static boolean pertenece(Conversacion conversacion, UserSummary usuario) {
        return conversacion.tipo() != TipoConversacion.SOPORTE
                || conversacion.esAprendizDeSoporte(usuario.id()) || usuario.role().canManageRoles();
    }

    private UserId mentorVigenteDe(Conversacion conversacion) {
        if (conversacion.tipo() != TipoConversacion.CELULA) {
            return null;
        }
        var grupo = mentorDeLosGrupos.deLosGrupos(List.of(conversacion.celulaId())).get(conversacion.celulaId());
        return grupo == null ? null : grupo.mentorId();
    }

    /** En un grupo el mentor primero y luego el staff; en un soporte el aprendiz; después, por nombre. */
    private static Comparator<UserSummary> orden(Conversacion conversacion, UserId mentor) {
        return Comparator.comparingInt((UserSummary u) -> prioridad(conversacion, u, mentor))
                .thenComparing(u -> sinTildes(nombreDe(u)))
                .thenComparing(u -> u.id().value());
    }

    private static int prioridad(Conversacion conversacion, UserSummary usuario, UserId mentor) {
        return switch (conversacion.tipo()) {
            case CELULA -> usuario.id().equals(mentor) ? 0 : rolDe(usuario, mentor) == RolEnElChat.APRENDIZ ? 2 : 1;
            case SOPORTE -> conversacion.esAprendizDeSoporte(usuario.id()) ? 0 : 1;
            case GLOBAL, DIRECTA -> 0;
        };
    }

    /** El mentor vigente del grupo lo es aunque su rol de cuenta sea otro; el resto, por su rol de cuenta. */
    private static RolEnElChat rolDe(UserSummary usuario, UserId mentorVigente) {
        if (usuario.id().equals(mentorVigente)) {
            return RolEnElChat.MENTOR;
        }
        UserRole rol = usuario.role();
        return switch (rol) {
            case TRAINEE -> RolEnElChat.APRENDIZ;
            case MENTOR, MENTOR_LEAD -> RolEnElChat.MENTOR;
            case ADMIN -> RolEnElChat.ADMIN;
            case ALCHEMIST -> RolEnElChat.ALQUIMISTA;
        };
    }

    private static String nombreDe(UserSummary usuario) {
        String nombre = usuario.fullName();
        return nombre == null || nombre.isBlank() ? "Miembro Renaser" : nombre.strip();
    }

    private static boolean coincide(UserSummary usuario, String consulta) {
        return consulta == null || consulta.isBlank()
                || sinTildes(nombreDe(usuario)).contains(sinTildes(consulta.strip()));
    }

    private static String sinTildes(String texto) {
        String descompuesto = Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return descompuesto.replaceAll("\\p{M}", "");
    }

    private void requireActivo(UserId actorId) {
        boolean activo = userSummaryFinder.findById(actorId).map(u -> u.status() == UserStatus.ACTIVE).orElse(false);
        if (!activo) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }
}
