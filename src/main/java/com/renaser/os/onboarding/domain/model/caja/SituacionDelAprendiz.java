package com.renaser.os.onboarding.domain.model.caja;

import com.renaser.os.shared.domain.DuracionDelPrograma;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * Lo que la caja necesita saber del aprendiz y que no es de la caja: si es un aprendiz con el programa
 * activado, si la cuenta está suspendida, si vive en Perú, y su calendario con los hábitos de la Fase 1.
 *
 * @param aplica       es APRENDIZ y activó su programa (si no, la caja no aplica)
 * @param zona         su zona horaria: su día es el de esta zona, no el del servidor (regla 02)
 * @param primeraFecha la fecha local de su Día 1 (ya corrida por los ajustes de día del Admin)
 * @param habitos      sus días con hábitos, al menos los de los días 1 a 7
 * @param duracionDias los días de SU programa (90, o 365 en el Diplomado de un año; spec 022): su día se acota a ellos
 */
public record SituacionDelAprendiz(boolean aplica, boolean suspendido, boolean delPeru, ZoneId zona,
                                   LocalDate primeraFecha, List<DiaDeHabitos> habitos, int duracionDias) {

    /** Firma de antes de las formaciones: el programa de 90 días. */
    public SituacionDelAprendiz(boolean aplica, boolean suspendido, boolean delPeru, ZoneId zona, LocalDate primeraFecha,
                                List<DiaDeHabitos> habitos) {
        this(aplica, suspendido, delPeru, zona, primeraFecha, habitos, DuracionDelPrograma.POR_DEFECTO);
    }

    public SituacionDelAprendiz {
        habitos = habitos == null ? List.of() : List.copyOf(habitos);
        duracionDias = DuracionDelPrograma.validar(duracionDias);
        if (aplica) {
            Objects.requireNonNull(zona, "zona");
            Objects.requireNonNull(primeraFecha, "primeraFecha");
        }
    }

    /** Alguien a quien la caja no le aplica (staff, o un aprendiz que todavía no activó su programa). */
    public static SituacionDelAprendiz noAplica(boolean suspendido, boolean delPeru) {
        return new SituacionDelAprendiz(false, suspendido, delPeru, null, null, List.of());
    }
}
