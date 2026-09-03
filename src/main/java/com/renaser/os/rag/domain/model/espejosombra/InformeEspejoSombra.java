package com.renaser.os.rag.domain.model.espejosombra;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Informe semanal del Espejo Sombra (tabla {@code informes_espejo_sombra}): el
 * análisis por IA de las entradas de diario de un aprendiz durante una semana —
 * patrón dominante, {@link DistribucionTemporal} (pasado/presente/futuro) y hasta
 * 10 {@link PreguntaConfrontacion}. Raíz de agregado con identidad y ciclo de vida
 * propios: cuelga de {@code participantes_programa}, no de {@code usuarios}
 * (docs/MODULO_RAG.md §2).
 *
 * <p>No tiene máquina de estados: un informe se genera una única vez por semana
 * (UNIQUE {@code participante_id, semana_inicio}) y no se edita — no hay verbos de
 * transición como en {@code Evidencia}. Contenido sensible (análisis psicológico
 * personal): CLAUDE.MD §5.4.9, nunca se loguea.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class InformeEspejoSombra {

    /** Espejo del CHECK {@code orden BETWEEN 1 AND 10} de {@code preguntas_confrontacion}: máximo 10 filas. */
    public static final int MAX_PREGUNTAS = 10;

    private final InformeEspejoSombraId id;
    private final UserId participanteId;
    private final LocalDate semanaInicio;
    private final int cantidadEntradas;
    private final String patronDominante;
    private final DistribucionTemporal distribucion;
    private final String insight;
    private final List<PreguntaConfrontacion> preguntas;
    private final Instant creadoEn;

    /**
     * Genera un informe nuevo a partir del resultado de la IA. La idempotencia por
     * semana (no generar dos veces para la misma semana) es responsabilidad del caso
     * de uso que orquesta esto ({@code EspejoSombraService.generar}), apoyada en el
     * UNIQUE de la tabla — este factory method no la conoce ni la necesita conocer.
     *
     * <p>El {@code id} entra por parámetro, no se genera acá: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code EspejoSombraService.generar}).
     * Así {@code generar} es referencialmente transparente y un test puede fijar el id que
     * espera, en vez de tener que caer a {@link #rehydrate} para lograrlo.
     */
    public static InformeEspejoSombra generar(InformeEspejoSombraId id, UserId participanteId,
                                                LocalDate semanaInicio, int cantidadEntradas,
                                                String patronDominante, DistribucionTemporal distribucion,
                                                String insight, List<PreguntaConfrontacion> preguntas, Clock clock) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(semanaInicio, "semanaInicio es obligatorio");
        Objects.requireNonNull(distribucion, "distribucion es obligatoria");
        Objects.requireNonNull(clock, "clock es obligatorio");
        requireTexto(patronDominante, "patronDominante");
        requireTexto(insight, "insight");
        requireCantidadEntradasValida(cantidadEntradas);
        List<PreguntaConfrontacion> preguntasValidas = requirePreguntasValidas(preguntas);
        return new InformeEspejoSombra(id, participanteId, semanaInicio, cantidadEntradas,
                patronDominante, distribucion, insight, preguntasValidas, clock.now());
    }

    /** Reconstruye desde persistencia — sin volver a validar invariantes de creación (ya pasaron una vez). */
    public static InformeEspejoSombra rehydrate(InformeEspejoSombraId id, UserId participanteId,
                                                  LocalDate semanaInicio, int cantidadEntradas,
                                                  String patronDominante, DistribucionTemporal distribucion,
                                                  String insight, List<PreguntaConfrontacion> preguntas,
                                                  Instant creadoEn) {
        return new InformeEspejoSombra(id, participanteId, semanaInicio, cantidadEntradas, patronDominante,
                distribucion, insight, List.copyOf(preguntas), creadoEn);
    }

    private static void requireTexto(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " es obligatorio");
        }
    }

    /** Espejo del CHECK {@code cantidad_entradas >= 0}. */
    private static void requireCantidadEntradasValida(int cantidadEntradas) {
        if (cantidadEntradas < 0) {
            throw new IllegalArgumentException("cantidadEntradas no puede ser negativo: " + cantidadEntradas);
        }
    }

    /**
     * Máximo {@link #MAX_PREGUNTAS} preguntas y ningún {@code orden} repetido dentro
     * del mismo informe — el rango individual (1..10) ya lo protege
     * {@link PreguntaConfrontacion} en su propio constructor.
     */
    private static List<PreguntaConfrontacion> requirePreguntasValidas(List<PreguntaConfrontacion> preguntas) {
        Objects.requireNonNull(preguntas, "preguntas no puede ser null (usar lista vacia)");
        if (preguntas.size() > MAX_PREGUNTAS) {
            throw new IllegalArgumentException(
                    "Maximo " + MAX_PREGUNTAS + " preguntas de confrontacion, llegaron " + preguntas.size());
        }
        Set<Integer> ordenesVistos = new HashSet<>();
        for (PreguntaConfrontacion pregunta : preguntas) {
            if (!ordenesVistos.add(pregunta.orden())) {
                throw new IllegalArgumentException("Orden de pregunta duplicado: " + pregunta.orden());
            }
        }
        return List.copyOf(preguntas);
    }
}
