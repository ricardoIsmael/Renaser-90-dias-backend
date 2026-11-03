package com.renaser.os.rag.api;

import com.renaser.os.rocks.api.Pareto8020Finder.CifrasDel8020;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * El 80/20 de una persona en una semana o un mes del programa, con el análisis de la IA cuando ya está (spec 031, D-306): lo que lee la
 * hoja «Tu 80/20» de Plan, las fichas de quien acompaña y SER.
 *
 * <p><b>No autoriza.</b> Quien llama (el controller de la persona, el del mentor, el del líder) ya decidió que el actor puede ver a esa
 * persona y, si es una ficha, que el interruptor «Código Renaser en las fichas» lo deja (spec 034): el análisis lleva lo que la persona
 * respondió en el Código Renaser, así que es del mismo dueño. Responde por el id que le dan, en SU zona y SU formación.
 *
 * <p><b>Es lectura pura:</b> nunca llama a la IA ni genera nada (FR-006). El análisis lo deja el barrido horario; abrirlo cien veces cuesta
 * lo mismo que abrirlo una.
 */
public interface Analisis8020Finder {

    /**
     * @param inicio el primer día del periodo (el que devolvió {@code anterior}/{@code siguiente}); {@code null} = el periodo en curso
     * @throws java.util.NoSuchElementException si la persona todavía no activó su programa o este no llegó a empezar
     * @throws IllegalArgumentException si ningún periodo empieza ese día o si todavía no llega
     */
    VistaDelAnalisis de(UserId personaId, Tipo tipo, LocalDate inicio);

    /** El análisis listo más reciente del tipo pedido, en la formación de la persona; vacío si todavía no tiene ninguno. Lo lee SER. */
    Optional<AnalisisLeido> ultimoListo(UserId personaId, Tipo tipo);

    enum Tipo {
        SEMANA,
        MES
    }

    /**
     * @param estado     {@code EN_CURSO} (el periodo no cerró: solo cifras), {@code LISTO}, {@code PENDIENTE} (cerró y el análisis llega
     *                   pronto), {@code SIN_DATOS} (no alcanzó el mínimo; ver {@code falta}) o {@code SIN_ANALISIS} (la IA no pudo)
     * @param anterior   el inicio del periodo anterior; {@code null} para el primero
     * @param siguiente  el inicio del periodo siguiente; {@code null} si es el actual
     * @param cifras     las del 80/20 del periodo: si ya está listo, las que el texto cita; si no, las de ahora
     * @param semanas    las semanas del mes con su avance; vacía en una semana
     * @param falta      con {@code SIN_DATOS}: {@code ACCIONES} y/o {@code DIAS}; vacía en los demás
     * @param minimoDeAcciones cuántas acciones hacen falta para analizar este periodo (para decirlo en la app)
     * @param codigoRenaser lo que se contó del Código Renaser; {@code null} si hubo pocas respuestas o no hay análisis
     * @param analisis   las cuatro partes; {@code null} salvo {@code LISTO}
     */
    record VistaDelAnalisis(Tipo tipo, int numero, LocalDate inicio, LocalDate fin, boolean cerrado, LocalDate anterior,
                            LocalDate siguiente, CifrasDel8020 cifras, List<ResumenDeSemana> semanas, String estado,
                            List<String> falta, int minimoDeAcciones, ResumenDelRadar codigoRenaser, TextoVisible analisis,
                            Instant generadoEn) {

        public VistaDelAnalisis {
            semanas = semanas == null ? List.of() : List.copyOf(semanas);
            falta = falta == null ? List.of() : List.copyOf(falta);
        }
    }

    /** Las cuatro partes del análisis, siempre en este orden. Texto de la IA sobre datos personales: quien lo reciba no lo loguea. */
    record TextoVisible(String loQueMasMovio, String loQueSeTeFue, String comoEstuviste, List<String> proximosPasos) {

        public TextoVisible {
            proximosPasos = List.copyOf(proximosPasos);
        }
    }

    /** Un análisis listo, para que SER lo explique. */
    record AnalisisLeido(Tipo tipo, int numero, LocalDate inicio, LocalDate fin, TextoVisible texto, Instant generadoEn) {
    }

    /** Una semana de un mes: para mirar la tendencia. */
    record ResumenDeSemana(int numero, int planificadas, int cumplidas, Integer porcentajeCumplidas,
                           Integer porcentajeDelImpactoDeLasVerdes) {
    }

    /** Lo que se contó del Código Renaser del periodo. Las energías van de 1 a 10 con un decimal; {@code null} donde no hubo respuestas. */
    record ResumenDelRadar(int respuestas, int dictadas, Double energiaPromedio, Integer energiaMinima, Integer energiaMaxima,
                           EnergiaPorMomento porMomento, List<EnergiaDeSemana> porSemana) {

        public ResumenDelRadar {
            porSemana = porSemana == null ? List.of() : List.copyOf(porSemana);
        }
    }

    /** Mañana (antes de las 12), tarde (de 12 a 17) y noche (desde las 18), en la hora local de la persona. */
    record EnergiaPorMomento(Double manana, Double tarde, Double noche) {
    }

    record EnergiaDeSemana(int numero, int respuestas, Double energiaPromedio) {
    }
}
