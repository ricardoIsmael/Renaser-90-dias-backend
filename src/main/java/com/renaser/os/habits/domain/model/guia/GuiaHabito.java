package com.renaser.os.habits.domain.model.guia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/** Guia (mantra + secciones) de un habito, valida desde `diaInicio` (tabla `guias_habito`). */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class GuiaHabito {

    private final GuiaHabitoId id;
    private final HabitoId habitoId;
    private final int diaInicio;
    private Integer diaFin;
    private String queHacer;
    private String comoHacerlo;
    private String ciencia;
    private String renaser;
    private String alquimia;
    private String resultados;
    private String mantraTitulo;
    private String mantraIntro;
    private String mantraCuerpo;
    private String referenciaFuente;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    public static GuiaHabito crear(HabitoId habitoId, int diaInicio, Instant ahora) {
        Objects.requireNonNull(habitoId, "habitoId es obligatorio");
        if (diaInicio < 1 || diaInicio > 90) {
            throw new IllegalArgumentException("diaInicio fuera de rango 1..90: " + diaInicio);
        }
        return new GuiaHabito(GuiaHabitoId.newId(), habitoId, diaInicio, null, null, null, null, null, null, null,
                null, null, null, null, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static GuiaHabito rehydrate(GuiaHabitoId id, HabitoId habitoId, int diaInicio, Integer diaFin,
                                        String queHacer, String comoHacerlo, String ciencia, String renaser,
                                        String alquimia, String resultados, String mantraTitulo, String mantraIntro,
                                        String mantraCuerpo, String referenciaFuente, Instant creadoEn,
                                        Instant actualizadoEn) {
        return new GuiaHabito(id, habitoId, diaInicio, diaFin, queHacer, comoHacerlo, ciencia, renaser, alquimia,
                resultados, mantraTitulo, mantraIntro, mantraCuerpo, referenciaFuente, creadoEn, actualizadoEn);
    }

    public void actualizarContenido(String queHacer, String comoHacerlo, String ciencia, String renaser,
                                     String alquimia, String resultados, Instant ahora) {
        this.queHacer = queHacer;
        this.comoHacerlo = comoHacerlo;
        this.ciencia = ciencia;
        this.renaser = renaser;
        this.alquimia = alquimia;
        this.resultados = resultados;
        this.actualizadoEn = ahora;
    }

    @Override
    public String toString() {
        return "GuiaHabito[" + id + ", " + habitoId + ", dia " + diaInicio + "]";
    }
}
