package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.ConsultarHabitosDeTodosUseCase.HabitoDelDia;
import com.renaser.os.leadership.application.ports.in.ConsultarHabitosDeTodosUseCase.HabitosDeTodos;
import com.renaser.os.leadership.application.ports.in.ConsultarHabitosDeTodosUseCase.Totales;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * «Hábitos de todos» (spec 029): un día, el resumen y la lista de hábitos con cuántos lo cumplieron. Proyección a mano. {@code category} es el código que ya
 * usa la API de hábitos (el inglés de las cuatro de la Escuela o la clave) y {@code categoryKey} la clave de la base.
 */
public record HabitosDeTodosResponse(LocalDate date, boolean isToday, LocalDate earliest, LocalDate latest, TotalesResponse totals,
                                      List<HabitoResponse> habits) {

    public static HabitosDeTodosResponse from(HabitosDeTodos habitos) {
        return new HabitosDeTodosResponse(habitos.fecha(), habitos.esHoy(), habitos.masAntigua(), habitos.masReciente(),
                TotalesResponse.from(habitos.totales()), habitos.habitos().stream().map(HabitoResponse::from).toList());
    }

    public record TotalesResponse(int students, int withHabits, int expected, int done, int pending, int missed) {

        static TotalesResponse from(Totales totales) {
            return new TotalesResponse(totales.alumnos(), totales.conHabitos(), totales.esperados(), totales.cumplieron(),
                    totales.pendientes(), totales.sinCumplir());
        }
    }

    public record HabitoResponse(UUID habitId, String title, String category, String categoryKey, String formation, int expected,
                                  int done, int pending, int missed) {

        static HabitoResponse from(HabitoDelDia habito) {
            return fromDelDia(habito);
        }

        public static HabitoResponse fromDelDia(HabitoDelDia habito) {
            return new HabitoResponse(habito.habitoId(), habito.titulo(), habito.categoriaCodigo(),
                    habito.categoriaClave(), habito.formacion(), habito.esperados(), habito.cumplieron(), habito.pendientes(),
                    habito.sinCumplir());
        }
    }
}
