package com.renaser.os.community.application.ports.out.categoria;

import com.renaser.os.community.domain.model.categoria.CategoriaMuro;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface LoadCategoriaMuroPort {

    Optional<CategoriaMuro> porClave(String clave);

    /** Activas, en el orden que fijo el panel — lo unico que necesita el editor del movil
     * (wall-categories/repository.ts:4-12). */
    List<CategoriaMuro> listarActivas();

    /** Todas, activas o no, para el panel de administracion. */
    List<CategoriaMuro> listarTodas();

    Set<String> listarClaves();

    int contarPublicaciones(String clave);
}
