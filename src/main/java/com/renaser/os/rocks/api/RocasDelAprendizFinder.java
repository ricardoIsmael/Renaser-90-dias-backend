package com.renaser.os.rocks.api;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato publico de {@code rocks} para LEER las rocas de un aprendiz desde otro modulo
 * (2026-09-23, herramienta {@code consultar_rocas} del acompanante, {@code rag}).
 *
 * <p><b>Delega, no reimplementa.</b> Cada metodo responde con los mismos casos de uso que sirven
 * {@code GET /rocks/today}, {@code /rocks/tomorrow}, {@code /rocks} (dashboard) y
 * {@code /rocks/monthly/plan}: la zona del participante, la ventana nocturna, el bloqueo Pareto
 * (Ley IV) y las compuertas de planificacion se resuelven ahi, en un solo lugar.
 *
 * <p><b>Misma autorizacion que la app.</b> A diferencia de {@code points.api.RocasDelDiaFinder}
 * (que devuelve vacio), estos metodos propagan lo mismo que el caso de uso:
 * {@code NoSuchElementException} si el participante no existe y {@code NotAuthorizedException}
 * si esta suspendido o no tiene el programa andando. El que llama decide como contarlo.
 *
 * <p>Lo que cruza la frontera son proyecciones con tipos de Java, nunca {@code RocaDiaria} ni
 * {@code EjeObjetivo}: el eje y el color viajan como el nombre de la constante.
 */
public interface RocasDelAprendizFinder {

    /** Las rocas de HOY en la zona del aprendiz, con el bloqueo Pareto ya resuelto. */
    RocasDelDia deHoy(UserId aprendizId);

    /** Las rocas de MANANA en la zona del aprendiz. El bloqueo Pareto no aplica todavia: va en {@code false}. */
    RocasDelDia deManana(UserId aprendizId);

    /** Los objetivos semanales de la semana de programa en curso. */
    RocasDeLaSemana deLaSemana(UserId aprendizId);

    /** El objetivo del mes en curso, uno por eje con Roca Maestra definida. */
    List<ObjetivoDelMesDelEje> delMes(UserId aprendizId);

    /**
     * El avance de la semana de programa en curso, tal como lo muestra el dashboard de la app, mas el
     * balance por eje de los dias ya terminados (D-177).
     */
    ProgresoDeLaSemana progresoDeLaSemana(UserId aprendizId);

    /** Los objetivos de los 90 dias (Rocas Maestras), uno por eje definido, con su meta medible si la tiene (D-177). */
    List<ObjetivoDeNoventaDias> objetivosDeNoventaDias(UserId aprendizId);

    /**
     * Lo que la persona escribio al cerrar la semana de programa ANTERIOR a la de hoy (D-177). Vacio
     * en la semana 1 y antes de empezar; con la lista de ejes vacia si esa semana no tuvo objetivos.
     */
    Optional<CierreDeLaSemanaAnterior> cierreDeLaSemanaAnterior(UserId aprendizId);

    /**
     * @param fecha         el dia consultado, en la zona del aprendiz
     * @param planificacion el estado del plan de manana, visto desde ahora
     */
    record RocasDelDia(LocalDate fecha, List<RocaDelDia> rocas, PlanificacionDeManana planificacion) {
    }

    /**
     * @param id                 el de {@code rocas_diarias}: el mismo que usan
     *                           {@code POST /rocks/{id}/evidence} y su {@code upload-url}. Lo sumo D-178
     *                           para que el acompanante pida la foto de UNA accion de hoy; no se le
     *                           muestra a la persona
     * @param completada         una roca se completa SOLO entregando evidencia (R-02): {@code false}
     *                           es, a la vez, "evidencia pendiente"
     * @param bloqueadaPorPareto Ley IV: hay que completar antes la VERDE de su eje
     * @param horaInicio         hora local; {@code null} si no se fijo
     * @param horaFin            hora local; {@code null} si no se fijo
     */
    record RocaDelDia(UUID id, String eje, int posicion, String color, String titulo, LocalTime horaInicio,
                      LocalTime horaFin, boolean completada, boolean bloqueadaPorPareto) {
    }

    /**
     * @param planCreado       ya hay rocas guardadas para manana
     * @param ventanaAbierta   la ventana nocturna de planificacion ({@code VentanaPlanificacionDiaria})
     *                         esta abierta ahora, en la zona del aprendiz
     * @param ventanaAbreA     hora local a la que abre esa ventana
     * @param puedeCrearPlan   la compuerta que usa la app ({@code DashboardRocas.puedeCrearPlanDiario}):
     *                         onboarding completo + ventana abierta + objetivo semanal para manana
     */
    record PlanificacionDeManana(boolean planCreado, int rocasPlanificadas, boolean ventanaAbierta,
                                 LocalTime ventanaAbreA, boolean puedeCrearPlan) {
    }

    /** {@code inicio}/{@code fin}: la semana de programa (siete dias del programa, D-192); la 13 termina el dia 90. */
    record RocasDeLaSemana(int numeroSemana, LocalDate inicio, LocalDate fin, List<RocaDeLaSemana> rocas) {
    }

    /**
     * @param editable             la ventana real de edicion (W-03)
     * @param revisada             ya tiene la autoevaluacion de cierre (W-04)
     * @param autoevaluacionInicio como arrancaba la semana en ese eje, 1 a 10; {@code null} si no la dijo
     */
    record RocaDeLaSemana(String eje, String titulo, String obstaculo, String contingencia, boolean editable,
                          boolean revisada, Integer autoevaluacionInicio) {
    }

    /**
     * @param hoy                    el dia de hoy en la zona del aprendiz
     * @param progresoSemanalPct     completadas/planificadas de los dias ya transcurridos (hoy incluido), 0-100
     * @param dias                   uno por fecha de la semana de programa, de su primer a su ultimo dia
     * @param ritmo                  nombre de {@code EstadoRitmoRocas}: {@code OK}, {@code LENTO} o {@code CRITICO}
     * @param diasCompletadosUltimos7 dias de los 7 anteriores a hoy con al menos una roca completada
     * @param porEje                 los tres ejes, con lo planificado y lo completado en los dias ya
     *                               terminados de esta semana (antes de hoy)
     * @param planificacionBloqueada Ley II: desde el dia 31, a partir de las 20:00, sin 3 rocas para manana
     */
    record ProgresoDeLaSemana(int numeroSemana, LocalDate inicio, LocalDate fin, LocalDate hoy,
                              int progresoSemanalPct, List<DiaDeLaSemana> dias, String ritmo,
                              int diasCompletadosUltimos7, List<BalanceDelEje> porEje,
                              PlanificacionDeManana planificacion, boolean planificacionBloqueada) {
    }

    /** {@code completadas} y {@code total} en {@code null} para un dia que no llego; {@code total} tambien sin plan. */
    record DiaDeLaSemana(LocalDate fecha, Integer completadas, Integer total, boolean esHoy) {
    }

    record BalanceDelEje(String eje, int planificadas, int completadas) {
    }

    /**
     * Una Roca Maestra. Los numeros viajan en {@code null} si el objetivo es solo cualitativo.
     *
     * @param avance         el valor que lleva hoy (con linea base) o lo acumulado (sin ella)
     * @param unidadAdelante la moneda va delante ({@code S/ 1500}), la unidad fisica detras ({@code 75 kg})
     * @param porcentaje     cuanto del camino recorrio, 0-100 ({@code MetaCuantitativa.porcentaje})
     */
    record ObjetivoDeNoventaDias(String eje, String objetivo, BigDecimal meta, BigDecimal avance, String unidad,
                                 boolean unidadAdelante, BigDecimal lineaBase, Integer porcentaje) {
    }

    record CierreDeLaSemanaAnterior(int numeroSemana, List<CierreDelEje> ejes) {
    }

    /** {@code autoevaluacionFin} en {@code null} = ese eje no se cerro; bloqueo y correccion tambien. */
    record CierreDelEje(String eje, String titulo, Integer autoevaluacionInicio, Integer autoevaluacionFin,
                        String bloqueoPrincipal, String correccion) {
    }

    /**
     * Una sola de estas variantes describe el mes: lo que la persona escribio ({@code tituloPropio}
     * y opcionalmente {@code cifra}), la cifra calculada, la meta ya alcanzada, o el motivo por el
     * que no hay cifra.
     *
     * @param tituloPropio    lo que la persona guardo para este mes; cuando existe, manda
     * @param cifra           lo que hay que lograr al cierre del mes (en una meta acumulada, lo que
     *                        hay que sumar ese mes); {@code null} si no hay cifra
     * @param unidadAdelante  la moneda va delante ({@code S/ 1500}), la unidad fisica detras ({@code 75 kg})
     * @param motivoSinCifra  nombre de {@code ObjetivoDelMes.MotivoSinCifra}, o {@code null}
     */
    record ObjetivoDelMesDelEje(String eje, int numeroMes, int diaDeCierre, String tituloPropio, BigDecimal cifra,
                                String unidad, boolean unidadAdelante, boolean metaAlcanzada,
                                String motivoSinCifra) {
    }
}
