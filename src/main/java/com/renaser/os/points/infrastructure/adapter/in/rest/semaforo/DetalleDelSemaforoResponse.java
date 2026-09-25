package com.renaser.os.points.infrastructure.adapter.in.rest.semaforo;

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
 * Respuesta de {@code GET /api/v1/me/semaforo} y de la pausa: el formato JSON EXACTO de
 * docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.1. Mapeo a mano (regla 04: nunca MapStruct hacia la
 * respuesta HTTP, un campo nuevo del dominio se filtraría solo). Cada color viaja con su palabra.
 */
public record DetalleDelSemaforoResponse(boolean aplica, boolean obligatorio, String zona, PausaResponse pausa,
                                         VentanaResponse vigente, List<SemanaResponse> semanas, Instant calculadoEn) {

    public static DetalleDelSemaforoResponse de(DetalleDelSemaforo detalle) {
        return new DetalleDelSemaforoResponse(detalle.aplica(), detalle.obligatorio(),
                detalle.zona() == null ? null : detalle.zona().getId(), PausaResponse.de(detalle.pausa()),
                VentanaResponse.de(detalle.vigente()),
                detalle.semanas().stream().map(SemanaResponse::de).toList(), detalle.calculadoEn());
    }

    public record PausaResponse(LocalDate desde, LocalDate hasta) {

        static PausaResponse de(PausaDelSemaforo pausa) {
            return pausa == null ? null : new PausaResponse(pausa.desde(), pausa.hasta());
        }
    }

    public record VentanaResponse(LocalDate desde, LocalDate hasta, BigDecimal porcentaje, ColorSemaforo color,
                                  String etiqueta, int diasConDatos, boolean cerrada, List<DiaResponse> dias) {

        static VentanaResponse de(VentanaDelSemaforo ventana) {
            if (ventana == null) {
                return null;
            }
            return new VentanaResponse(ventana.desde(), ventana.hasta(), ventana.porcentaje(), ventana.color(),
                    ventana.color().etiqueta(), ventana.diasConDatos(), ventana.cerrada(),
                    ventana.dias().stream().map(DiaResponse::de).toList());
        }
    }

    public record DiaResponse(LocalDate fecha, String estado, Integer porcentaje, ColorSemaforo color,
                              String etiqueta, ConteoResponse habitos, ConteoResponse objetivos) {

        static DiaResponse de(DiaDelSemaforo dia) {
            return new DiaResponse(dia.fecha(), dia.estado().name(), dia.porcentaje(), dia.color(),
                    dia.color().etiqueta(),
                    new ConteoResponse(dia.habitosProgramados(), dia.habitosCumplidos()),
                    new ConteoResponse(dia.objetivosProgramados(), dia.objetivosCumplidos()));
        }
    }

    public record ConteoResponse(int programados, int cumplidos) {
    }

    public record SemanaResponse(LocalDate desde, LocalDate hasta, BigDecimal porcentaje, ColorSemaforo color,
                                 String etiqueta, int diasConDatos, Instant cerradaEn) {

        static SemanaResponse de(SemanaCerrada semana) {
            return new SemanaResponse(semana.desde(), semana.hasta(), semana.porcentaje(), semana.color(),
                    semana.color().etiqueta(), semana.diasConDatos(), semana.cerradaEn());
        }
    }
}
