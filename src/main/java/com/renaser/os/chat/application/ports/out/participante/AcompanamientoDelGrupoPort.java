package com.renaser.os.chat.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.UUID;

/**
 * Quién acompaña a quién hoy dentro de un grupo, para abrir los chats de dos (D-173).
 *
 * <p>No es {@link PertenenciaVigentePort}: aquella responde "quién está", sin importar con qué
 * función, porque el chat del grupo es de todos. Esta separa a los aprendices de quienes los
 * acompañan, y deja afuera al staff que cubre el grupo.
 */
public interface AcompanamientoDelGrupoPort {

    List<UserId> aprendicesVigentes(UUID celulaId);

    /** Mentor y guías vigentes. Vacío si el grupo no está operativo. */
    List<UserId> acompanantesVigentes(UUID celulaId);
}
