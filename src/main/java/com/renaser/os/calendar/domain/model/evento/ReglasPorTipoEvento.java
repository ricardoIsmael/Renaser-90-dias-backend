package com.renaser.os.calendar.domain.model.evento;

import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Puerto directo de {@code src/features/calendar/eventTypes.ts} (repo viejo) —
 * ÚNICA fuente de los recordatorios por defecto y de si un tipo exige elegibilidad
 * especial. DOMINIO PURO, sin Spring.
 *
 * <p><b>Que NO se porto de eventTypes.ts</b>: la "plantilla" de recurrencia
 * ({@code recurrence: {frequency, byWeekday}}), la hora/duracion/ubicacion por defecto.
 * En el repo viejo esos campos son solo PRELLENADO del formulario del panel admin —
 * {@code service.ts}/{@code schema.ts} nunca los valida ni los fuerza contra el evento
 * ya guardado (el admin puede elegir cualquier fecha, duracion o ubicacion sin importar el
 * tipo). No son una regla de negocio del backend, son un detalle de UI del cliente que la
 * crea — si el nuevo panel admin los necesita, es prellenado de formulario del lado del
 * cliente, no logica de este modulo.
 */
public final class ReglasPorTipoEvento {

    /** Hora de la alarma de la Semana de Manifestacion — confirmado con el negocio 2026-08-05 (eventTypes.ts). */
    public static final LocalTime HORA_ALARMA_MANIFESTACION = LocalTime.of(4, 50);

    private static final Map<TipoEvento, List<ReglaRecordatorio>> RECORDATORIOS = new EnumMap<>(TipoEvento.class);
    private static final Map<TipoEvento, Boolean> REQUIERE_ELEGIBILIDAD = new EnumMap<>(TipoEvento.class);

    static {
        RECORDATORIOS.put(TipoEvento.MENTORIA_ALQUIMISTA, List.of(ReglaRecordatorio.minutosAntes(1, 10)));
        REQUIERE_ELEGIBILIDAD.put(TipoEvento.MENTORIA_ALQUIMISTA, true);

        RECORDATORIOS.put(TipoEvento.ESPONTANEO, List.of(
                ReglaRecordatorio.horaDelDia(1, LocalTime.of(6, 0)),
                ReglaRecordatorio.minutosAntes(2, 10)));
        REQUIERE_ELEGIBILIDAD.put(TipoEvento.ESPONTANEO, false);

        RECORDATORIOS.put(TipoEvento.SEMANA_MANIFESTACION, List.of(
                ReglaRecordatorio.diasAntes(1, 1),
                ReglaRecordatorio.horaDelDia(2, HORA_ALARMA_MANIFESTACION)));
        REQUIERE_ELEGIBILIDAD.put(TipoEvento.SEMANA_MANIFESTACION, false);

        RECORDATORIOS.put(TipoEvento.SESION_ESPECIAL, List.of(
                ReglaRecordatorio.horaDelDia(1, LocalTime.of(6, 0)),
                ReglaRecordatorio.minutosAntes(2, 10)));
        REQUIERE_ELEGIBILIDAD.put(TipoEvento.SESION_ESPECIAL, false);
    }

    private ReglasPorTipoEvento() {
    }

    /** Recordatorios que aplican cuando el evento NO define los suyos propios (reglasEfectivas en Evento). */
    public static List<ReglaRecordatorio> recordatoriosPorDefecto(TipoEvento tipo) {
        return RECORDATORIOS.get(tipo);
    }

    /**
     * Hoy solo MENTORIA_ALQUIMISTA (mentoriaEligibility.ts, repo viejo): requiere un %
     * de cumplimiento semanal de habitos+rocas que este modulo NO calcula — ver
     * {@code ConsultarElegibilidadEventoPort} y CL-xx en docs/MODULO_CALENDAR.md §6.
     */
    public static boolean requiereElegibilidad(TipoEvento tipo) {
        return REQUIERE_ELEGIBILIDAD.get(tipo);
    }
}
