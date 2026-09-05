package com.renaser.os.habits.domain.model.habito;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/** Espejo de `tipo_dia` (baseline SQL). DISCIPLINA/INTOXICACION derivan del dia
 * de programa (ciclos fijos); TODOS aplica cualquier dia; DOMINGO es especial. */
public enum TipoDia {
    DISCIPLINA,
    INTOXICACION,
    TODOS,
    DOMINGO;

    /**
     * DOMINGO por dia de calendario; DISCIPLINA en cualquier otro caso. INTOXICACION (ciclos
     * fijos del repo viejo) NO esta implementado en esta version — ver docs/MODULO_HABITS.md.
     * Regla pura: la comparten la generacion de registros y la lectura de horarios vigentes.
     */
    public static TipoDia delDia(LocalDate fecha) {
        return fecha.getDayOfWeek() == DayOfWeek.SUNDAY ? DOMINGO : DISCIPLINA;
    }

    /**
     * En que dias de la SEMANA cae este tipo de dia. Es la inversa de {@link #delDia(LocalDate)} y
     * vive aca, en el dominio, por el mismo motivo que aquella: es una regla de negocio, no un
     * detalle de presentacion.
     *
     * <p>La necesita el planificador semanal del movil, que hasta ahora marcaba TODOS los habitos
     * como activos los 7 dias porque el catalogo no le decia otra cosa — y por eso los habitos de
     * DOMINGO ({@code DESCANSO PROFUNDO}, {@code RITUAL DE MAÑANA}, {@code AGUA E HIDRATACIÓN})
     * aparecian tambien de lunes a sabado. Deducirlo del titulo no es opcion: {@code DESCANSO
     * PROFUNDO} es de domingo y no lo dice, y el titulo ademas es renombrable (misma razon por la
     * que V18 lo descarto como criterio).
     *
     * <p>INTOXICACION no depende del calendario semanal sino del dia de programa, y ademas no esta
     * implementado en esta version: se responde el conjunto vacio en vez de inventar dias.
     */
    public Set<DayOfWeek> diasDeLaSemana() {
        return switch (this) {
            case TODOS -> EnumSet.allOf(DayOfWeek.class);
            case DOMINGO -> EnumSet.of(DayOfWeek.SUNDAY);
            case DISCIPLINA -> EnumSet.complementOf(EnumSet.of(DayOfWeek.SUNDAY));
            case INTOXICACION -> EnumSet.noneOf(DayOfWeek.class);
        };
    }
}
