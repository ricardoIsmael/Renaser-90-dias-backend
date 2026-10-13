package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Lo que está apagado para una sesión (contrato §6, {@code GET /funcionalidades/mias}). Las hijas de una madre apagada
 * figuran todas, para que la app no tenga que conocer el árbol; lo que no figura está prendido.
 */
public interface ConsultarMisFuncionalidadesUseCase {

    MisFuncionalidades mias(UserId actor);

    record MisFuncionalidades(long version, List<FuncionalidadApagada> apagadas) {
    }

    /** @param aviso el que ve la persona; nunca vacío (sin aviso escrito, el genérico) */
    record FuncionalidadApagada(String clave, String aviso) {
    }
}
