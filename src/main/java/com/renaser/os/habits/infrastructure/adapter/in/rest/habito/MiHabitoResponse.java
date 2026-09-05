package com.renaser.os.habits.infrastructure.adapter.in.rest.habito;

import com.renaser.os.habits.application.ports.in.habito.ConsultarMisHabitosUseCase.HabitoConDias;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitCategoryDto;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitEvidenceRequirementDto;
import com.renaser.os.habits.infrastructure.adapter.in.rest.habitoadmin.HabitTypeDto;

import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;

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
 *
 * <p>{@code activeWeekdays} son los dias de la semana en que el habito aplica, derivados del
 * {@code TipoDia} de sus horarios. Existe porque el planificador semanal del movil marcaba TODOS
 * los habitos como activos los 7 dias —no tenia de donde sacar otra cosa— y por eso los tres
 * habitos de DOMINGO aparecian tambien de lunes a sabado. Se mandan como nombres de
 * {@link DayOfWeek} ({@code "MONDAY"}..{@code "SUNDAY"}) y en orden de la semana, no como un
 * booleano "solo domingo": el dia que aparezca otro tipo de dia el contrato no cambia.
 */
public record MiHabitoResponse(String id, String title, String description, HabitTypeDto habitType,
                                HabitCategoryDto category, HabitEvidenceRequirementDto evidenceRequirement,
                                boolean isOptional, boolean isSystemHabit, boolean isDeactivatable,
                                String systemKey, List<String> activeWeekdays, int unlockDay,
                                int daysUntilUnlock, boolean locked) {

    public static MiHabitoResponse from(HabitoConDias vista) {
        return new MiHabitoResponse(vista.habito().id().value().toString(), vista.habito().titulo(),
                vista.habito().descripcion(), HabitTypeDto.from(vista.habito().tipo()),
                HabitCategoryDto.fromClave(vista.habito().categoriaClave()),
                HabitEvidenceRequirementDto.from(vista.habito().exigenciaEvidencia()),
                vista.habito().esOpcional(), vista.habito().esDeSistema(), vista.habito().desactivable(),
                vista.habito().claveSistema(), diasOrdenados(vista.diasSemana()), vista.diaDesbloqueo(),
                vista.diasParaDesbloqueo(), vista.bloqueado());
    }

    private static List<String> diasOrdenados(java.util.Set<DayOfWeek> dias) {
        return dias.stream().sorted(Comparator.comparing(DayOfWeek::getValue)).map(DayOfWeek::name).toList();
    }

    /**
     * Para el alta de un habito PERSONAL, que devuelve el habito recien creado sin volver a
     * consultarlo: {@code MisHabitosService.crear} le crea siempre un horario {@code TODOS}, asi
     * que sus dias se derivan de ahi y no se escriben a mano.
     */
    /** Un habito PERSONAL recien creado esta disponible desde ya: no tiene dia de desbloqueo. */
    private static final int PRIMER_DIA = 1;

    public static MiHabitoResponse from(Habito habito, java.util.Set<DayOfWeek> diasSemana) {
        return new MiHabitoResponse(habito.id().value().toString(), habito.titulo(), habito.descripcion(),
                HabitTypeDto.from(habito.tipo()), HabitCategoryDto.fromClave(habito.categoriaClave()),
                HabitEvidenceRequirementDto.from(habito.exigenciaEvidencia()), habito.esOpcional(),
                habito.esDeSistema(), habito.desactivable(), habito.claveSistema(),
                diasOrdenados(diasSemana), PRIMER_DIA, 0, false);
    }
}
