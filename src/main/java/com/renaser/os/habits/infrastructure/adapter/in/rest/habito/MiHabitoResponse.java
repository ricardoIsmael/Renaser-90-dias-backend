package com.renaser.os.habits.infrastructure.adapter.in.rest.habito;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitCategoryDto;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitEvidenceRequirementDto;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitTypeDto;

/**
 * Proyeccion a mano — nunca la entidad completa (CLAUDE.MD §5.4.1).
 *
 * <p>{@code isDeactivatable} es de solo lectura: no existe forma de que un aprendiz lo escriba
 * (no esta en {@code CreatePersonalHabitRequest} ni en {@code UpdateHabitPreferenceRequest} — ver
 * docs/informes/habits-campo-desactivable.md). Los 4 habitos con {@code isDeactivatable=false}
 * los fija la migracion V18; hoy no hay caso de uso que pueda cambiarlo en caliente.
 *
 * <p>{@code systemKey} expone {@code Habito.claveSistema} — la identidad FUNCIONAL estable de un
 * habito de catalogo ({@code DAILY_CLASS}, {@code PASTILLA_RENACER}...), {@code null} en los
 * habitos PERSONAL. Se agrega porque el movil necesita reconocer un habito puntual (hoy: la Clase
 * Diaria, que abre su propio flujo de resumen) y el unico criterio que tenia era el TITULO, que es
 * renombrable por el propio aprendiz ({@code HabitRenameController}) — el mismo motivo por el que
 * V18__habitos_desactivable.sql descarta el titulo como criterio. De solo lectura: ningun request
 * la escribe (las claves las siembra la migracion baseline, ver {@code Habito.crearDeSistema}).
 */
public record MiHabitoResponse(String id, String title, String description, HabitTypeDto habitType,
                                HabitCategoryDto category, HabitEvidenceRequirementDto evidenceRequirement,
                                boolean isOptional, boolean isSystemHabit, boolean isDeactivatable,
                                String systemKey) {

    public static MiHabitoResponse from(Habito habito) {
        return new MiHabitoResponse(habito.id().value().toString(), habito.titulo(), habito.descripcion(),
                HabitTypeDto.from(habito.tipo()), HabitCategoryDto.fromClave(habito.categoriaClave()),
                HabitEvidenceRequirementDto.from(habito.exigenciaEvidencia()), habito.esOpcional(),
                habito.esDeSistema(), habito.desactivable(), habito.claveSistema());
    }
}
