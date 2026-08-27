package com.renaser.os.chat.application.ports.in.miembro;

import com.renaser.os.shared.domain.UserId;

/**
 * Ficha del grupo GLOBAL (#28): todos sus participantes, los CINCO roles. A diferencia
 * de {@link ListarDirectorioMiembrosUseCase} esto es informativo ("quien es miembro de
 * este grupo"), no un directorio para escribir — por eso no filtra por
 * {@code UserStatus} ni excluye al actor de la lista.
 */
public interface ListarMiembrosGlobalUseCase {

    /** Solo lo pueden ver los participantes de GLOBAL (que, por el auto-join, es
     * practicamente todo usuario activo — igual se verifica, nunca se asume). */
    PaginaMiembros listar(UserId actorId, UserId cursor, int limite);
}
