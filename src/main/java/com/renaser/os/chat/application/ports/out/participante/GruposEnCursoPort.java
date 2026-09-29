package com.renaser.os.chat.application.ports.out.participante;

import java.util.List;
import java.util.UUID;

/**
 * Qué grupos están corriendo HOY, sin preguntar por nadie (D-225).
 *
 * <p>No es {@link PertenenciaVigentePort}: aquella responde "¿esta persona está en este grupo?", y el
 * Admin ve el chat de un grupo sin estar en él. Lo que se le exige al grupo es lo mismo que a sus
 * integrantes —dentro de su periodo—, así que un grupo vencido o cerrado queda oculto para el Admin
 * igual que para su mentor y sus aprendices.
 */
public interface GruposEnCursoPort {

    boolean estaEnCurso(UUID celulaId);

    /** Todos los grupos en curso, recepción incluida, con o sin mentor. */
    List<UUID> gruposEnCurso();
}
