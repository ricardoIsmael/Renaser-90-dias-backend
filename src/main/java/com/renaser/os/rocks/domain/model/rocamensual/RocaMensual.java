package com.renaser.os.rocks.domain.model.rocamensual;

import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;

import java.time.Instant;
import java.util.Objects;

/**
 * Roca Mensual: el tramo de un objetivo de 90 dias que tiene que estar logrado al cierre de un mes.
 *
 * <p>Es el escalon que faltaba entre la Roca Maestra y la semanal. El plan del programa baja en
 * cuatro niveles —maestro a 90 dias, mensual, semanal y las tres actividades del dia— y hasta
 * ahora se saltaba del objetivo grande directo a la semana.
 *
 * <p><b>Cuelga de la Roca Maestra, no del participante.</b> Un objetivo mensual solo significa
 * algo como tramo de un objetivo mayor: "facturar 10.000 este mes" se entiende porque la meta de
 * los 90 dias son 30.000. Colgarlo de la maestra hace que "tres objetivos mensuales" salga solo de
 * la misma regla que ya da tres semanales —hay tres maestras, una por eje, y una mensual por
 * maestra y por mes— en vez de exigir un contador aparte que alguien pueda desincronizar. Es la
 * misma forma que {@code RocaSemanal}.
 *
 * <p>La parte medible es opcional y reusa {@link MetaCuantitativa}, con la misma regla que la
 * maestra: las tres juntas o ninguna. Un tramo puede ser cualitativo; media meta no.
 */
public record RocaMensual(RocaMensualId id, RocaMaestraId rocaMaestraId, int numeroMes, String titulo,
                           MetaCuantitativa meta, Instant creadoEn, Instant actualizadoEn) {

    /** Mismo tope que {@code RocaSemanal}/{@code RocaDiaria}: la columna es {@code text} y no acota. */
    public static final int MAX_TITULO = 500;

    public RocaMensual {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(rocaMaestraId, "rocaMaestraId es obligatorio");
        Objects.requireNonNull(creadoEn, "creadoEn es obligatorio");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn es obligatorio");
        if (!MesPrograma.esValido(numeroMes)) {
            throw new IllegalArgumentException("El mes tiene que estar entre 1 y " + MesPrograma.MESES);
        }
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("El titulo del objetivo mensual es obligatorio");
        }
        titulo = titulo.trim();
        if (titulo.length() > MAX_TITULO) {
            throw new IllegalArgumentException("El titulo no puede pasar de " + MAX_TITULO + " caracteres");
        }
    }

    /** Objetivo mensual recien definido. {@code meta} puede ser null: tramo cualitativo. */
    public static RocaMensual definir(RocaMensualId id, RocaMaestraId rocaMaestraId, int numeroMes, String titulo,
                                       MetaCuantitativa meta, Instant ahora) {
        return new RocaMensual(id, rocaMaestraId, numeroMes, titulo, meta, ahora, ahora);
    }

    /**
     * Mismo tramo, otro contenido. Conserva identidad, maestra, mes y fecha de creacion: es el
     * objetivo de ese mes corregido, no uno nuevo.
     */
    public RocaMensual redefinir(String nuevoTitulo, MetaCuantitativa nuevaMeta, Instant ahora) {
        return new RocaMensual(id, rocaMaestraId, numeroMes, nuevoTitulo, nuevaMeta, creadoEn, ahora);
    }

    /** {@code false} = tramo cualitativo, sin barra de avance que dibujar. */
    public boolean tieneMeta() {
        return meta != null;
    }

    /** Dia de programa en que cierra este mes: 30, 60 o 90. */
    public int diaDeCierre() {
        return MesPrograma.ultimoDiaDe(numeroMes);
    }

    /** Solo para el adaptador de persistencia: reconstruye una roca mensual ya existente. */
    public static RocaMensual rehydrate(RocaMensualId id, RocaMaestraId rocaMaestraId, int numeroMes, String titulo,
                                         MetaCuantitativa meta, Instant creadoEn, Instant actualizadoEn) {
        return new RocaMensual(id, rocaMaestraId, numeroMes, titulo, meta, creadoEn, actualizadoEn);
    }
}
