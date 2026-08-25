package com.renaser.os.community.application.ports.out.categoria;

import com.renaser.os.community.domain.model.categoria.CategoriaMuro;

import java.util.List;

public interface SaveCategoriaMuroPort {

    CategoriaMuro save(CategoriaMuro categoria);

    /** Reordena en una sola transaccion — un fallo a mitad deja media lista con el orden
     * nuevo y la otra media con el viejo (wall-categories/repository.ts:60-72). */
    void reordenar(List<String> clavesEnOrden);
}
