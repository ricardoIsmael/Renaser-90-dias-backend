package com.renaser.os.points.infrastructure.adapter.in.rest.home;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.application.ports.in.home.ConsultarResumenHomeUseCase.ResumenHome;
import com.renaser.os.users.api.FasePrograma;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /api/v1/home}. {@code bloqueos} documenta, en la propia respuesta, que datos
 * siguen sin poder componerse y por que — ver javadoc de {@code ConsultarResumenHomeUseCase}.
 * {@code habitosHoy}/{@code proximoEvento}/{@code notificacionesNoLeidas} pueden venir
 * {@code null}: es una falla parcial de ESE widget, no de toda la respuesta (mismo criterio
 * documentado en {@code HomeAgregadoService}).
 */
public record ResumenHomeResponse(int puntosLiga, BigDecimal coherencia, int rachaActual, int rachaMaxima,
                                   int diaPrograma, boolean inscrito, FasePrograma fase,
                                   HabitosHoyResponse habitosHoy, RocasHoyResponse rocasHoy,
                                   ProximoEventoResponse proximoEvento, Long notificacionesNoLeidas,
                                   List<String> bloqueos, SemaforoResponse semaforo) {

    public static ResumenHomeResponse from(ResumenHome resumen) {
        return new ResumenHomeResponse(resumen.puntosLiga(), resumen.coherencia(), resumen.rachaActual(),
                resumen.rachaMaxima(), resumen.diaPrograma(), resumen.inscrito(), resumen.fase(),
                HabitosHoyResponse.from(resumen.habitosHoy()), RocasHoyResponse.from(resumen.rocasHoy()),
                ProximoEventoResponse.from(resumen.proximoEvento()), resumen.notificacionesNoLeidas(),
                resumen.bloqueos(), SemaforoResponse.from(resumen.semaforo()));
    }

    /**
     * Campo nuevo y aditivo (D-168): la app instalada lo ignora. Cada color viaja con su palabra
     * (RL-30). {@code null} si la persona no se mide.
     */
    public record SemaforoResponse(ColorSemaforo color, String etiqueta, BigDecimal porcentaje, int diasConDatos,
                                   boolean pausado, List<DiaCompactoResponse> dias) {

        static SemaforoResponse from(ResumenHome.SemaforoHoyResumen resumen) {
            return resumen == null ? null : new SemaforoResponse(resumen.color(), resumen.color().etiqueta(),
                    resumen.porcentaje(), resumen.diasConDatos(), resumen.pausado(),
                    resumen.dias().stream().map(DiaCompactoResponse::from).toList());
        }
    }

    /** Un día de la ventana vigente, lo justo para dibujar las 7 barras de la tarjeta. */
    public record DiaCompactoResponse(LocalDate fecha, String estado, Integer porcentaje, ColorSemaforo color,
                                      String etiqueta) {

        static DiaCompactoResponse from(DiaDelSemaforo dia) {
            return new DiaCompactoResponse(dia.fecha(), dia.estado().name(), dia.porcentaje(), dia.color(),
                    dia.color().etiqueta());
        }
    }

    public record HabitosHoyResponse(int completados, int total) {

        static HabitosHoyResponse from(ResumenHome.HabitosHoyResumen resumen) {
            return resumen == null ? null : new HabitosHoyResponse(resumen.completados(), resumen.total());
        }
    }

    public record RocasHoyResponse(int completadas, int total) {

        static RocasHoyResponse from(ResumenHome.RocasHoyResumen resumen) {
            return resumen == null ? null : new RocasHoyResponse(resumen.completadas(), resumen.total());
        }
    }

    public record ProximoEventoResponse(UUID eventoId, String titulo, Instant iniciaEn) {

        static ProximoEventoResponse from(ResumenHome.ProximoEventoResumen resumen) {
            return resumen == null ? null
                    : new ProximoEventoResponse(resumen.eventoId(), resumen.titulo(), resumen.iniciaEn());
        }
    }
}
