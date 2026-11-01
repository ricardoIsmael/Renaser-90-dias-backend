package com.renaser.os.mentoring.api;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El semáforo vigente de los grupos regulares, agrupado por su mentor: lo que la gestión del Líder de
 * Mentores muestra junto a cada mentor (SDD 002; D-241).
 *
 * <p>Es la MISMA medición que el resumen por grupos del líder ({@code GET /api/v1/semaforo/groups}) —el
 * mismo padrón de {@code MedicionDeGrupos} y el mismo {@code ResumenDelGrupo}—, así que el líder nunca
 * ve dos cifras distintas del mismo grupo. La recepción queda fuera, igual que allí.
 *
 * <p>Los ids de aprendices viajan solo para que quien consume pueda cruzarlos con otra fuente (los
 * tickets abiertos de ese grupo); nunca nombres. Quien expone esto por HTTP no los devuelve (RL-07).
 */
public interface MedicionDeMentoresFinder {

    /**
     * Solo los grupos de las formaciones de {@code alcance} (spec 022): un líder con la Escuela activa no ve los grupos del Diplomado de una mentora que
     * lleva los dos, ni el semáforo de sus aprendices. Los indicadores por mentor salen de esos grupos, no de todos los suyos.
     */
    MedicionVigente vigente(AlcanceDeFormacion alcance);

    /** Todas las formaciones: lo que ve quien las ve todas. */
    default MedicionVigente vigente() {
        return vigente(AlcanceDeFormacion.todas());
    }

    /**
     * @param porMentor el resumen sobre TODOS los aprendices de los grupos de cada mentor (un mentor
     *                  puede llevar varios, D-141). Sin clave = ese usuario no lidera un grupo regular hoy.
     */
    record MedicionVigente(LocalDate desde, LocalDate hasta, boolean cerrada, List<GrupoMedido> grupos,
                           Map<UserId, SemaforoResumido> porMentor) {
    }

    record GrupoMedido(UUID grupoId, String nombre, UserId mentorId, List<UserId> aprendices,
                       SemaforoResumido semaforo) {
    }

    /**
     * @param promedio de los aprendices con datos, 1 decimal; null si ninguno tiene. Nunca cero.
     * @param color    el del promedio (VERDE/AMARILLO/ROJO/SIN_DATOS), con su palabra en {@code etiqueta}
     */
    record SemaforoResumido(int verde, int amarillo, int rojo, int sinDatos, BigDecimal promedio, String color,
                            String etiqueta) {

        public int total() {
            return verde + amarillo + rojo + sinDatos;
        }
    }
}
