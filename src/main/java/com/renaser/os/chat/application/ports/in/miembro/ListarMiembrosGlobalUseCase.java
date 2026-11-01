package com.renaser.os.chat.application.ports.in.miembro;

import com.renaser.os.shared.domain.Formacion;
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
    default PaginaMiembros listar(UserId actorId, UserId cursor, int limite) {
        return listar(actorId, cursor, limite, null);
    }

    /**
     * Spec 024: la comunidad general es una por formación; la ficha es la de la formación de quien pide. Solo Administración y Alquimista (que
     * ven las tres) eligen una; sin elegir, juntan las tres.
     */
    PaginaMiembros listar(UserId actorId, UserId cursor, int limite, Formacion formacion);
}
