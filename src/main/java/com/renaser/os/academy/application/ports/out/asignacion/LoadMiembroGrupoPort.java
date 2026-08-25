package com.renaser.os.academy.application.ports.out.asignacion;

import com.renaser.os.academy.domain.model.asignacion.GrupoId;
import com.renaser.os.shared.domain.UserId;

import java.util.Set;

public interface LoadMiembroGrupoPort {

    /** Usuarios miembros de cualquiera de los grupos dados. */
    Set<UserId> usuariosDeGrupos(Set<GrupoId> grupoIds);
}
