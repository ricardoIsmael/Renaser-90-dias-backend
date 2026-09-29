package com.renaser.os.community.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * El nombre de cada grupo y quién es su mentor HOY, para nombrar el chat del grupo (D-221, pedido del
 * dueño del 29/09: «Luisa y sus aprendices»).
 *
 * <p>Interfaz aparte de {@link AcompanamientoFinder} a propósito: aquella ya tiene cuatro dobles de
 * prueba escritos a mano en otros módulos, y sumarle un método abstracto obligaba a tocarlos todos
 * para algo que ninguno usa.
 *
 * <p>«Mentor vigente» es la asignación con función MENTOR abierta en ese instante (la misma fuente que
 * {@code MisCelulasService} y {@code gruposOperativos}): {@code celulas.mentor_id} nombra a uno solo y
 * desde D-141 no es la fuente de verdad. Un grupo de recepción (guías) o sin mentor devuelve
 * {@code mentorId = null}.
 */
public interface MentorVigenteFinder {

    /**
     * Uno por cada grupo pedido que exista; los que no existen no figuran. En lote: la lista de chats
     * trae todos los grupos de la persona de una vez.
     */
    List<GrupoConSuMentor> deLosGrupos(Collection<UUID> grupoIds, Instant instante);

    /** @param mentorId su mentor vigente; {@code null} si no tiene */
    record GrupoConSuMentor(UUID grupoId, String nombre, UserId mentorId) {
    }
}
