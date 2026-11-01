package com.renaser.os.habits.application.ports.out.habito;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoadHabitoPort {

    Optional<Habito> byId(HabitoId id);

    /** UNA sola consulta para N ids — para proyecciones de lectura (hueco #10), nunca N+1. */
    List<Habito> porIds(Collection<HabitoId> ids);

    List<Habito> catalogoActivo();

    /** Catalogo SISTEMA completo (activos e inactivos) — panel admin, hueco #11. */
    List<Habito> catalogoCompleto();

    List<Habito> personalesActivosDe(UserId participanteId);

    /**
     * Lookup por identidad funcional estable del catalogo (ej. {@code DAILY_CLASS}) — ver {@code SelectorHabito.PorClaveSistema}. Spec 023:
     * la clave es unica POR FORMACION (un habito de flujo propio copiado a otra formacion conserva la suya), asi que se pide siempre la de
     * la formacion de quien hace el flujo. Vacio si esa formacion no ofrece ese habito: el flujo no existe alli (FR-005).
     */
    Optional<Habito> porClaveSistema(String claveSistema, Formacion formacion);
}
