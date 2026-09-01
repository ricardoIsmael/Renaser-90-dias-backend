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

    /**
     * El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code GuiaHabitoAdminService}).
     */
    public static GuiaHabito crear(GuiaHabitoId id, HabitoId habitoId, int diaInicio, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(habitoId, "habitoId es obligatorio");
        if (diaInicio < 1 || diaInicio > 90) {
            throw new IllegalArgumentException("diaInicio fuera de rango 1..90: " + diaInicio);
        }
        return new GuiaHabito(id, habitoId, diaInicio, null, null, null, null, null, null, null,
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

    /** Si esta guia rige para ese dia de programa — mismo criterio que {@code HorarioHabito.aplicaEnDia}. */
    public boolean aplicaEnDia(int diaPrograma) {
        return diaPrograma >= diaInicio && (diaFin == null || diaPrograma <= diaFin);
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

    /**
     * Edicion completa desde el panel admin (hueco #11): los 6 textos +
     * mantra + referencia de fuente, todo lo que {@link ContenidoGuia} agrupa.
     * {@code habitoId}/{@code diaInicio} no se tocan — identifican la guia, no su
     * contenido (ver {@code UpsertGuiaHabitoUseCase}: cambiar el tramo de dias
     * es "crear otra guia", no editar esta).
     */
    public void actualizarContenidoCompleto(ContenidoGuia contenido, Instant ahora) {
        Objects.requireNonNull(contenido, "contenido es obligatorio");
        this.queHacer = contenido.queHacer();
        this.comoHacerlo = contenido.comoHacerlo();
        this.ciencia = contenido.ciencia();
        this.renaser = contenido.renaser();
        this.alquimia = contenido.alquimia();
        this.resultados = contenido.resultados();
        this.mantraTitulo = contenido.mantraTitulo();
        this.mantraIntro = contenido.mantraIntro();
        this.mantraCuerpo = contenido.mantraCuerpo();
        this.referenciaFuente = contenido.referenciaFuente();
        this.actualizadoEn = ahora;
    }

    /**
     * Cierra la vigencia de esta guia en {@code diaFinInclusive} — usado por
     * {@code closePrevious} (hueco #11): al dar de alta una guia nueva que empieza en el
     * dia N, la guia anterior (con {@code diaFin == null}, abierta) se cierra en N-1 para
     * que los rangos de {@link #aplicaEnDia} no se pisen.
     */
    public void cerrarEn(int diaFinInclusive, Instant ahora) {
        if (diaFinInclusive < diaInicio) {
            throw new IllegalArgumentException("diaFin no puede ser anterior a diaInicio (" + diaInicio + ")");
        }
        this.diaFin = diaFinInclusive;
        this.actualizadoEn = ahora;
    }

    /** Fija (o quita, con {@code null}) el dia de cierre — panel admin, hueco #11. */
    public void establecerDiaFin(Integer diaFin, Instant ahora) {
        if (diaFin != null && diaFin < diaInicio) {
            throw new IllegalArgumentException("diaFin no puede ser anterior a diaInicio (" + diaInicio + ")");
        }
        this.diaFin = diaFin;
        this.actualizadoEn = ahora;
    }

    @Override
    public String toString() {
        return "GuiaHabito[" + id + ", " + habitoId + ", dia " + diaInicio + "]";
    }
}
