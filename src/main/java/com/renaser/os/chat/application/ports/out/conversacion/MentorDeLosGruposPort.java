package com.renaser.os.chat.application.ports.out.conversacion;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * El nombre de cada grupo y su mentor de HOY, para nombrar el chat del grupo (D-221). Lo responde
 * {@code community}; {@code chat} solo conoce el {@code celulaId} de la conversación.
 */
public interface MentorDeLosGruposPort {

    /** Por grupo que exista; los que no existen no figuran. En una sola pasada para toda la lista. */
    Map<UUID, GrupoConSuMentor> deLosGrupos(Collection<UUID> celulaIds);

    /** @param mentorId su mentor vigente, o {@code null} si no tiene (recepción, grupo sin mentor) */
    record GrupoConSuMentor(String nombreDelGrupo, UserId mentorId) {
    }
}
