package com.renaser.os.rag.application.ports.out.rocas;

import com.renaser.os.shared.domain.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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

    record RocasDelDia(LocalDate fecha, List<RocaDelDia> rocas, PlanDeManana planDeManana) {
    }

    /** {@code completada=false} es "evidencia pendiente": una roca solo se completa con evidencia. */
    record RocaDelDia(String eje, int posicion, String color, String titulo, LocalTime horaInicio,
                      LocalTime horaFin, boolean completada, boolean bloqueadaPorPareto) {
    }

    record PlanDeManana(boolean creado, int rocasPlanificadas, boolean ventanaAbierta, LocalTime ventanaAbreA,
                        boolean puedeCrearlo) {
    }

    record RocasDeLaSemana(int numeroSemana, LocalDate inicio, LocalDate fin, List<RocaDeLaSemana> rocas) {
    }

    record RocaDeLaSemana(String eje, String titulo, String obstaculo, String contingencia, boolean editable,
                          boolean revisada) {
    }

    record ObjetivoDelMes(String eje, int numeroMes, int diaDeCierre, String tituloPropio, BigDecimal cifra,
                          String unidad, boolean unidadAdelante, boolean metaAlcanzada, String motivoSinCifra) {
    }
}
