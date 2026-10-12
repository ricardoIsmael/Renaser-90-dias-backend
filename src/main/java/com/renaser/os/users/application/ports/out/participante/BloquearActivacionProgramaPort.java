package com.renaser.os.users.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;

/** Serializa las elecciones de una cuenta hasta el commit, incluso si aún no tiene participación. */
public interface BloquearActivacionProgramaPort {
    void porUsuario(UserId usuarioId);
}
