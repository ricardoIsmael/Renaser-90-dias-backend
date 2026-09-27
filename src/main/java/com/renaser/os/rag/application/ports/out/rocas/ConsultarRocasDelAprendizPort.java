package com.renaser.os.rag.application.ports.out.rocas;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto propio de {@code rag} para leer las rocas del aprendiz con el que habla el acompanante
 * (2026-09-23, herramienta {@code consultar_rocas}). Las tablas son de {@code rocks}: el adaptador
 * delega en {@code rocks.api.RocasDelAprendizFinder} (D-41), que a su vez usa los mismos casos de
 * uso que la app.
 *
 * <p>Mismo criterio que {@code ConsultarAgendaHabitosPort}: tipos propios, para que un cambio del
 * contrato ajeno quede contenido en el adaptador.
 *
 * <p>Todos los metodos propagan lo que propaga {@code rocks}: {@code NoSuchElementException} y
 * {@code NotAuthorizedException} cuando la persona no tiene el programa de rocas disponible.
 */
public interface ConsultarRocasDelAprendizPort {

    RocasDelDia deHoy(UserId aprendizId);

    RocasDelDia deManana(UserId aprendizId);

    RocasDeLaSemana deLaSemana(UserId aprendizId);

    List<ObjetivoDelMes> delMes(UserId aprendizId);

    /** D-177: el avance de la semana de programa en curso, como lo ve la app, y el balance por eje. */
    ProgresoDeLaSemana progresoDeLaSemana(UserId aprendizId);

    /** D-177: el objetivo de los 90 dias por eje (Roca Maestra). */
    List<ObjetivoDeNoventaDias> objetivosDeNoventaDias(UserId aprendizId);

    /** D-177: el cierre de la semana anterior; vacio en la semana 1. */
    Optional<CierreDeLaSemanaAnterior> cierreDeLaSemanaAnterior(UserId aprendizId);

    record RocasDelDia(LocalDate fecha, List<RocaDelDia> rocas, PlanDeManana planDeManana) {
    }

    /**
     * {@code completada=false} es "evidencia pendiente": una roca solo se completa con evidencia.
     * {@code id} es el de la roca diaria (D-178): con el, {@code proponer_registrar_accion_con_foto}
     * pide la foto de esa accion.
     */
    record RocaDelDia(UUID id, String eje, int posicion, String color, String titulo, LocalTime horaInicio,
                      LocalTime horaFin, boolean completada, boolean bloqueadaPorPareto) {
    }

    record PlanDeManana(boolean creado, int rocasPlanificadas, boolean ventanaAbierta, LocalTime ventanaAbreA,
                        boolean puedeCrearlo) {
    }

    /** {@code inicio}/{@code fin} en {@code null} mientras la persona no eligio su Dia 1: la semana 1 no tiene fechas (D-203). */
    record RocasDeLaSemana(int numeroSemana, LocalDate inicio, LocalDate fin, List<RocaDeLaSemana> rocas) {
    }

    record RocaDeLaSemana(String eje, String titulo, String obstaculo, String contingencia, boolean editable,
                          boolean revisada, Integer autoevaluacionInicio) {
    }

    /**
     * {@code inicio}/{@code fin}: como en {@link RocasDeLaSemana}, en {@code null} sin Dia 1 elegido.
     *
     * @param porEje  lo planificado y completado por eje en los dias ya terminados (antes de {@code hoy})
     * @param ritmo   {@code OK}, {@code LENTO} o {@code CRITICO}, de los 7 dias anteriores a hoy
     */
    record ProgresoDeLaSemana(int numeroSemana, LocalDate inicio, LocalDate fin, LocalDate hoy,
                              int progresoSemanalPct, List<DiaDeLaSemana> dias, String ritmo,
                              int diasCompletadosUltimos7, List<BalanceDelEje> porEje, PlanDeManana planDeManana,
                              boolean planificacionBloqueada) {
    }

    /** {@code completadas}/{@code total} en {@code null}: el dia no llego; {@code total} tambien: sin plan. */
    record DiaDeLaSemana(LocalDate fecha, Integer completadas, Integer total, boolean esHoy) {
    }

    record BalanceDelEje(String eje, int planificadas, int completadas) {
    }

    /** Los numeros en {@code null}: objetivo solo cualitativo. */
    record ObjetivoDeNoventaDias(String eje, String objetivo, BigDecimal meta, BigDecimal avance, String unidad,
                                 boolean unidadAdelante, BigDecimal lineaBase, Integer porcentaje) {
    }

    record CierreDeLaSemanaAnterior(int numeroSemana, List<CierreDelEje> ejes) {
    }

    /** {@code autoevaluacionFin} en {@code null}: ese eje no se cerro. */
    record CierreDelEje(String eje, String titulo, Integer autoevaluacionInicio, Integer autoevaluacionFin,
                        String bloqueoPrincipal, String correccion) {
    }

    record ObjetivoDelMes(String eje, int numeroMes, int diaDeCierre, String tituloPropio, BigDecimal cifra,
                          String unidad, boolean unidadAdelante, boolean metaAlcanzada, String motivoSinCifra) {
    }
}
