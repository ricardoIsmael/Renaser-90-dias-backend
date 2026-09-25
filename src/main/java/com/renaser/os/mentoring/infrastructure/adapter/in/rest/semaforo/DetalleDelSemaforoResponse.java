package com.renaser.os.mentoring.infrastructure.adapter.in.rest.semaforo;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.PausaDelSemaforo;
import com.renaser.os.points.api.SemanaCerrada;
import com.renaser.os.points.api.VentanaDelSemaforo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * El detalle del semáforo de una persona, en el JSON exacto de
 * docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.1: «un solo formato para 4 rutas». Estas son las dos
 * de {@code mentoring} (mentor y administración); {@code /me/semaforo} es de {@code points} y tiene
 * que devolver lo mismo campo por campo.
 *
 * <p>{@code aplica=false} llega con {@code vigente: null}, {@code semanas: []} y {@code pausa: null}:
 * es lo que trae {@link DetalleDelSemaforo#noAplica} y acá no se inventa nada.
 */
public record DetalleDelSemaforoResponse(boolean aplica, boolean obligatorio, String zona, PausaResponse pausa,
                                         VentanaResponse vigente, List<SemanaResponse> semanas,
                                         Instant calculadoEn) {

    public static DetalleDelSemaforoResponse from(DetalleDelSemaforo detalle) {
        return new DetalleDelSemaforoResponse(detalle.aplica(), detalle.obligatorio(),
                detalle.zona() == null ? null : detalle.zona().getId(), PausaResponse.from(detalle.pausa()),
                VentanaResponse.from(detalle.vigente()),
                detalle.semanas().stream().map(SemanaResponse::from).toList(), detalle.calculadoEn());
    }

    public record PausaResponse(LocalDate desde, LocalDate hasta) {

        static PausaResponse from(PausaDelSemaforo pausa) {
            return pausa == null ? null : new PausaResponse(pausa.desde(), pausa.hasta());
        }
    }

    /** {@code dias} siempre trae los 7 días, del más viejo al más nuevo (los manda {@code points}). */
    public record VentanaResponse(LocalDate desde, LocalDate hasta, BigDecimal porcentaje, String color,
                                  String etiqueta, int diasConDatos, boolean cerrada, List<DiaResponse> dias) {

        static VentanaResponse from(VentanaDelSemaforo ventana) {
            if (ventana == null) {
                return null;
            }
            return new VentanaResponse(ventana.desde(), ventana.hasta(), ventana.porcentaje(),
                    ventana.color().name(), ventana.color().etiqueta(), ventana.diasConDatos(), ventana.cerrada(),
                    ventana.dias().stream().map(DiaResponse::from).toList());
        }
    }

    /** {@code porcentaje} null y {@code color} SIN_DATOS si el día no es MEDIDO; la palabra siempre va. */
    public record DiaResponse(LocalDate fecha, String estado, Integer porcentaje, String color, String etiqueta,
                              ConteoResponse habitos, ConteoResponse objetivos) {

        static DiaResponse from(DiaDelSemaforo dia) {
            return new DiaResponse(dia.fecha(), dia.estado().name(), dia.porcentaje(), dia.color().name(),
                    dia.color().etiqueta(),
                    new ConteoResponse(dia.habitosProgramados(), dia.habitosCumplidos()),
                    new ConteoResponse(dia.objetivosProgramados(), dia.objetivosCumplidos()));
        }
    }

    public record ConteoResponse(int programados, int cumplidos) {
    }

    public record SemanaResponse(LocalDate desde, LocalDate hasta, BigDecimal porcentaje, String color,
                                 String etiqueta, int diasConDatos, Instant cerradaEn) {

        static SemanaResponse from(SemanaCerrada semana) {
            ColorSemaforo color = semana.color();
            return new SemanaResponse(semana.desde(), semana.hasta(), semana.porcentaje(),
                    color == null ? null : color.name(), color == null ? null : color.etiqueta(),
                    semana.diasConDatos(), semana.cerradaEn());
        }
    }
}
