package com.renaser.os.habits.infrastructure.adapter.in.rest.categoria;

import com.renaser.os.habits.application.ports.in.categoria.CategoriaDelCatalogo;
import com.renaser.os.habits.domain.model.categoria.CategoriaHabito;
import com.renaser.os.habits.domain.model.categoria.CodigoDeCategoria;

/** Una categoria como la ve Administracion, con cuantos habitos tiene y que se puede hacer con ella hoy. */
public record CategoriaAdminResponse(String key, String code, String formation, String name, String icon, int order,
                                      boolean isActive, int activeHabits, int totalHabits, boolean canBeDeactivated,
                                      boolean canBeDeleted) {

    public static CategoriaAdminResponse from(CategoriaDelCatalogo delCatalogo) {
        CategoriaHabito c = delCatalogo.categoria();
        return new CategoriaAdminResponse(c.clave(), CodigoDeCategoria.deClave(c.clave()), c.formacion().codigo(), c.nombre(),
                c.icono(), c.orden(), c.activa(), delCatalogo.habitosActivos(), delCatalogo.habitosTotales(),
                delCatalogo.puedeDesactivarse(), delCatalogo.puedeEliminarse());
    }
}
