package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.out.conversacion.MentorDeLosGruposPort;
import com.renaser.os.chat.application.ports.out.conversacion.MentorDeLosGruposPort.GrupoConSuMentor;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.NombreDelChat;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * El nombre con el que se MUESTRA cada chat (D-221), el mismo para aprendiz, mentor, Admin y
 * Alquimista: en la lista, en la cabecera, en la info y en el título de su aviso.
 *
 * <p>Todo en lote: una consulta a {@code community} por los grupos y una a {@code users} por los
 * nombres, para toda la lista de una vez (nunca una por fila). La regla de cada nombre vive en
 * {@link NombreDelChat}; acá solo se juntan los datos.
 *
 * <ul>
 *   <li>GLOBAL: el que guarda la conversación (el Admin puede renombrarla, #28).</li>
 *   <li>CELULA: «&lt;mentor&gt; y sus aprendices», o el nombre del grupo si no tiene mentor.</li>
 *   <li>SOPORTE: «&lt;aprendiz&gt; – Formación Renaser»; si el aprendiz ya no existe, lo guardado.</li>
 *   <li>DIRECTA: {@code null}. Se llama como la otra persona, y eso depende de quién mira: lo resuelve
 *       quien llama ({@code otroParticipanteNombre} en la lista, el autor en el aviso).</li>
 * </ul>
 */
@Service
public class NombresDeLosChatsService {

    private final MentorDeLosGruposPort mentorDeLosGrupos;
    private final UserSummaryFinder userSummaryFinder;

    public NombresDeLosChatsService(MentorDeLosGruposPort mentorDeLosGrupos, UserSummaryFinder userSummaryFinder) {
        this.mentorDeLosGrupos = mentorDeLosGrupos;
        this.userSummaryFinder = userSummaryFinder;
    }

    /** Las que no tienen nombre (DIRECTA, o un grupo que ya no existe) no figuran en el mapa. */
    public Map<ConversacionId, String> nombresDe(Collection<Conversacion> conversaciones) {
        Map<UUID, GrupoConSuMentor> grupos = mentorDeLosGrupos.deLosGrupos(celulasDe(conversaciones));
        Map<UserId, UserSummary> personas = personasQueNombran(conversaciones, grupos);
        Map<ConversacionId, String> nombres = new HashMap<>();
        for (Conversacion conversacion : conversaciones) {
            String nombre = nombreDe(conversacion, grupos, personas);
            if (nombre != null) {
                nombres.put(conversacion.id(), nombre);
            }
        }
        return nombres;
    }

    /** El de una sola conversación; {@code null} en una DIRECTA. */
    public String nombreDe(Conversacion conversacion) {
        return nombresDe(List.of(conversacion)).get(conversacion.id());
    }

    private static String nombreDe(Conversacion c, Map<UUID, GrupoConSuMentor> grupos,
                                   Map<UserId, UserSummary> personas) {
        return switch (c.tipo()) {
            case GLOBAL -> c.nombre();
            case DIRECTA -> null;
            case CELULA -> nombreDelGrupo(grupos.get(c.celulaId()), personas);
            case SOPORTE -> c.aprendizDelSoporte().map(personas::get)
                    .map(aprendiz -> NombreDelChat.deSoporte(aprendiz.fullName()))
                    .orElse(c.nombre());
        };
    }

    private static String nombreDelGrupo(GrupoConSuMentor grupo, Map<UserId, UserSummary> personas) {
        if (grupo == null) {
            return null;
        }
        UserSummary mentor = grupo.mentorId() == null ? null : personas.get(grupo.mentorId());
        return NombreDelChat.deGrupo(mentor == null ? null : mentor.fullName(), grupo.nombreDelGrupo());
    }

    private static List<UUID> celulasDe(Collection<Conversacion> conversaciones) {
        return conversaciones.stream()
                .filter(c -> c.tipo() == TipoConversacion.CELULA)
                .map(Conversacion::celulaId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /** Los mentores de los grupos y los aprendices de los soportes, en UNA consulta. */
    private Map<UserId, UserSummary> personasQueNombran(Collection<Conversacion> conversaciones,
                                                        Map<UUID, GrupoConSuMentor> grupos) {
        List<UserId> ids = Stream.concat(
                        grupos.values().stream().map(GrupoConSuMentor::mentorId),
                        conversaciones.stream().flatMap(c -> c.aprendizDelSoporte().stream()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return ids.isEmpty() ? Map.of() : userSummaryFinder.findByIds(ids);
    }
}
