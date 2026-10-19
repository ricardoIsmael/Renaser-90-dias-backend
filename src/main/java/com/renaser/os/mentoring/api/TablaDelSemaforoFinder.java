package com.renaser.os.mentoring.api;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * "La tabla del semáforo de este grupo": la que ya ven el mentor y el administrador, con nombres, para que
 * {@code leadership} arme la lista del líder (spec 005, R-3) sin una segunda fórmula de cumplimiento (FR-008).
 *
 * <p><b>No autoriza</b>, igual que {@link SemanaDeSeguimientoFinder}: quien llama ya decidió que el actor puede mirar
 * ese grupo. Es lo mismo que {@code GET /admin/semaforo/groups/{g}} responde, con el mismo padrón (solo aprendices
 * vigentes con la cuenta activa, sin el mentor que además cursa) y el mismo semáforo leído en lote.
 */
public interface TablaDelSemaforoFinder {

    /**
     * @param semanaHasta null = la ventana vigente (los últimos 7 días cerrados de cada aprendiz); si viene, un
     *                    viernes: la semana sábado→viernes que termina ese día, o {@link IllegalArgumentException}
     * @throws java.util.NoSuchElementException si el grupo no existe
     */
    TablaDeGrupo tablaDe(UUID grupoId, LocalDate semanaHasta);

    /**
     * @param filas      en el orden del semáforo (rojo, amarillo, sin datos, verde); quien la use puede reordenar
     * @param desde      primer día de la ventana de la tabla
     * @param cerrada    true solo si es una semana ya cerrada el sábado (su resultado ya no cambia)
     */
    record TablaDeGrupo(UUID grupoId, String grupoNombre, LocalDate desde, LocalDate hasta, boolean cerrada,
                        List<FilaDeLaTabla> filas) {

        public TablaDeGrupo {
            filas = filas == null ? List.of() : List.copyOf(filas);
        }
    }

    /**
     * @param color  {@link ColorSemaforo#SIN_DATOS} si no se mide o no tiene días medidos
     * @param motivo el nombre de {@code MotivoSinDatos} (por qué está «Sin datos»); null si tiene color
     * @param dias   los días de su ventana, del más viejo al más nuevo; vacío si no se mide
     */
    record FilaDeLaTabla(UUID personaId, String nombre, String avatarUrl, BigDecimal porcentaje,
                         ColorSemaforo color, int diasConDatos, List<DiaDelSemaforo> dias, String motivo) {

        public FilaDeLaTabla {
            dias = dias == null ? List.of() : List.copyOf(dias);
        }
    }
}
