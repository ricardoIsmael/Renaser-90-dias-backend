package com.renaser.os.calendar.domain.model.meet;

import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.evento.Excepcion;
import com.renaser.os.calendar.domain.model.evento.ExpansorOcurrencias;
import com.renaser.os.calendar.domain.model.evento.Ocurrencia;
import com.renaser.os.calendar.domain.model.meet.PlanDeSalas.OcurrenciaDeseada;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Las fechas de un evento que tienen que tener sala de Meet (spec 040, FR-017). Un evento que no se repite tiene una. Uno que se repite tiene una
 * por cada ocurrencia desde hoy hasta {@code ventanaDias} días adelante, sin las canceladas: más lejos no hay sala todavía, y no hace falta porque
 * la app tampoco las muestra. Cada fecha se identifica por su día en la ZONA DEL EVENTO, nunca por el del servidor (FR-018).
 */
public final class OcurrenciasDeseadas {

    /** Si el evento no dice cuánto dura, Google necesita un fin: se supone una hora. */
    public static final int DURACION_POR_DEFECTO_MINUTOS = 60;

    private OcurrenciasDeseadas() {
    }

    public static Map<LocalDate, OcurrenciaDeseada> de(Evento evento, List<Excepcion> excepciones, Instant ahora, int ventanaDias) {
        ZoneId zona = evento.timezone();
        Map<LocalDate, OcurrenciaDeseada> deseadas = new TreeMap<>();
        if (!evento.esRecurrente()) {
            LocalDate fecha = fechaDe(evento.iniciaEn(), zona);
            deseadas.put(fecha, deseada(fecha, evento.iniciaEn(), evento.duracionMinutos(), evento.titulo()));
            return deseadas;
        }
        Instant desde = ahora.atZone(zona).toLocalDate().atStartOfDay(zona).toInstant();
        Instant hasta = ahora.plus(Duration.ofDays(ventanaDias));
        List<Ocurrencia> ocurrencias = ExpansorOcurrencias.expandir(evento.iniciaEn(), evento.duracionMinutos(), zona,
                evento.recurrencia(), desde, hasta, excepciones);
        for (Ocurrencia ocurrencia : ocurrencias) {
            String titulo = ocurrencia.titulo() != null ? ocurrencia.titulo() : evento.titulo();
            LocalDate fecha = fechaDe(ocurrencia.inicioOcurrencia(), zona);
            deseadas.put(fecha, deseada(fecha, ocurrencia.iniciaEn(), ocurrencia.duracionMinutos(), titulo));
        }
        return deseadas;
    }

    /** El día de un instante, en la zona del evento. */
    public static LocalDate fechaDe(Instant instante, ZoneId zona) {
        return instante.atZone(zona).toLocalDate();
    }

    /** La clave de la sala es el día del SLOT original; el inicio es el efectivo, por si una excepción movió la ocurrencia. */
    private static OcurrenciaDeseada deseada(LocalDate fecha, Instant inicio, Integer duracionMinutos, String titulo) {
        int minutos = duracionMinutos == null ? DURACION_POR_DEFECTO_MINUTOS : duracionMinutos;
        return new OcurrenciaDeseada(fecha, inicio, inicio.plus(Duration.ofMinutes(minutos)), titulo);
    }
}
