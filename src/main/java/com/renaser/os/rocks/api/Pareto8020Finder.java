package com.renaser.os.rocks.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * El 80/20 de las acciones del día de una persona entre dos fechas (spec 031, D-306): cuántas planificó y cumplió y cuánto del
 * impacto cumplido vino de las que más mueven. Son <b>cifras calculadas por el sistema</b>; la IA las interpreta y nunca las calcula
 * (FR-002). Lo leen la hoja «Tu 80/20» de Plan, las fichas de quien acompaña y el análisis con IA de {@code rag}.
 *
 * <p>Las dos reglas de «el 20 %» van juntas (decisión del dueño 031-1, A y B): las acciones <b>verdes</b> (la regla de Pareto del
 * producto: la verde va primero en cada eje) y el 20 % de las <b>cumplidas con más impacto</b> (la cuenta clásica). No cambia la regla
 * de Pareto ni sus candados, y no da ni quita puntos: solo lee.
 *
 * <p><b>No autoriza</b>, igual que {@link AccionesDelDiaFinder}: quien llama ya estableció que el actor puede ver a esa persona.
 * Responde por el id que le dan y por SU zona (el «hoy» sale del reloj en su zona, nunca del servidor).
 */
public interface Pareto8020Finder {

    /**
     * @param desde inclusivo, fecha local de la persona ya resuelta por quien llama
     * @param hasta inclusivo; lo que va después de «hoy» no cuenta como planificado (todavía no llegó) y se informa en {@code porVenir}
     */
    InsumosDel8020 insumos(UserId personaId, LocalDate desde, LocalDate hasta);

    /** Solo las cifras, sin las notas de «Cierra tu día». */
    default CifrasDel8020 cifras(UserId personaId, LocalDate desde, LocalDate hasta) {
        return insumos(personaId, desde, hasta).cifras();
    }

    /**
     * @param notasDeCierre lo que la persona escribió al cerrar sus días de ese periodo (la nota de cada acción pendiente y la del
     *                      día), recortado y acotado. <b>Texto personal</b>: quien lo reciba no lo loguea ni lo devuelve a la app.
     */
    record InsumosDel8020(CifrasDel8020 cifras, List<String> notasDeCierre) {

        public InsumosDel8020 {
            notasDeCierre = List.copyOf(notasDeCierre);
        }
    }

    /**
     * @param planificadas       acciones de {@code desde} a hoy (o a {@code hasta}, si ya pasó)
     * @param cumplidas          las que tienen evidencia
     * @param diasConAcciones    días distintos con al menos una acción planificada
     * @param porVenir           acciones planificadas para días que todavía no llegan
     * @param impactoPlanificado suma del puntaje de impacto (1 a 10) de las planificadas
     * @param impactoCumplido    suma del puntaje de impacto de las cumplidas
     * @param porcentajeCumplidas {@code cumplidas / planificadas}, entero de 0 a 100; {@code null} si no hay planificadas
     * @param verdes             la regla A: las acciones verdes
     * @param veintePorCiento    la regla B: el 20 % de las cumplidas con más impacto; {@code null} con menos de cinco cumplidas (una sola
     *                           acción que explica «el 100 %» no es un hallazgo)
     * @param porEje             los ejes con acciones, en el orden de siempre
     * @param lasQueMasMovieron  las cumplidas con más impacto (las de la regla B), hasta cinco
     * @param lasQueSeFueron     las verdes o de mayor impacto que quedaron pendientes en un día que ya pasó, hasta cinco
     * @param cumplioLoQueMenosMovia hubo algo de menos impacto cumplido mientras lo que más movía quedó pendiente
     */
    record CifrasDel8020(LocalDate desde, LocalDate hasta, int planificadas, int cumplidas, int diasConAcciones, int porVenir,
                         int impactoPlanificado, int impactoCumplido, Integer porcentajeCumplidas, ReglaDeLasVerdes verdes,
                         ReglaDelVeintePorCiento veintePorCiento, List<CifrasDelEje> porEje,
                         List<AccionDel8020> lasQueMasMovieron, List<AccionDel8020> lasQueSeFueron,
                         boolean cumplioLoQueMenosMovia, ResumenDelCierre cierre) {

        public CifrasDel8020 {
            porEje = List.copyOf(porEje);
            lasQueMasMovieron = List.copyOf(lasQueMasMovieron);
            lasQueSeFueron = List.copyOf(lasQueSeFueron);
        }

        /** Si hay algo que contar: sin acciones planificadas el 80/20 no dice nada. */
        public boolean hayAcciones() {
            return planificadas > 0;
        }
    }

    /** Regla A: {@code porcentajeDelImpacto} es la parte del impacto cumplido que vino de las verdes; {@code null} sin impacto cumplido. */
    record ReglaDeLasVerdes(int planificadas, int cumplidas, int impactoCumplido, Integer porcentajeDelImpacto) {
    }

    /** Regla B: {@code acciones} es cuántas son ese 20 % y {@code impacto} cuánto suman; {@code porcentajeDelImpacto} es su parte del impacto cumplido. */
    record ReglaDelVeintePorCiento(int acciones, int impacto, Integer porcentajeDelImpacto) {
    }

    /** {@code eje} es el nombre de la constante ({@code CUERPO}, {@code TRABAJO}, {@code RELACIONES}). */
    record CifrasDelEje(String eje, int planificadas, int cumplidas, int impactoCumplido, Integer porcentajeDelImpacto) {
    }

    /** {@code color} es {@code VERDE}, {@code AMARILLA} o {@code ROJA}; el título lo escribió la persona. */
    record AccionDel8020(String eje, String color, int impacto, LocalDate fecha, String titulo) {
    }

    /**
     * Lo que dijo al cerrar sus días (spec 017): cuántos cerró, cuántos no, y cuántas veces apareció cada motivo y cada plan de mañana
     * en las acciones pendientes. Solo cuentas: lo escrito va aparte, en {@link InsumosDel8020#notasDeCierre()}.
     *
     * @param diasSinCerrar días ya terminados con acciones que no se cerraron (el de hoy, abierto, no cuenta)
     */
    record ResumenDelCierre(int diasCerrados, int diasSinCerrar, List<Repeticion> motivos, List<Repeticion> planes) {

        public ResumenDelCierre {
            motivos = List.copyOf(motivos);
            planes = List.copyOf(planes);
        }
    }

    /** {@code clave} es el nombre de la constante del motivo o del plan. */
    record Repeticion(String clave, int veces) {
    }
}
