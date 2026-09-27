package com.renaser.os.notifications.domain.model.evento;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * El aviso que recibe una persona por un recordatorio de evento del calendario (D-182): que dice,
 * adonde lleva al tocarlo, con que clave se deduplica y cuando ya no tiene sentido mandarlo.
 * Dominio puro: el instante actual entra como argumento.
 *
 * <p><b>La hora se dice en la zona del evento, y "hoy"/"mañana" tambien</b> (regla 02 §1). La
 * alarma de 04:50 de la Semana de Manifestacion, en Lima, sale a las 09:50 UTC; un recordatorio
 * de las 21:00 de Lima sale a las 02:00 UTC del dia SIGUIENTE. Comparar fechas UTC diria "mañana"
 * de algo que es hoy, o "hoy" de algo que es mañana.
 *
 * @param recordatorioId  id de la fila de {@code recordatorios_evento}
 * @param eventoId        el evento, para la ruta de la app
 * @param tituloEvento    el titulo tal como lo escribio quien creo el evento (maximo 30 caracteres)
 * @param inicio          el inicio de ESTA ocurrencia
 * @param zona            la zona del evento
 * @param esAnuncio       {@code true} si es el aviso "hay un evento nuevo", no un recordatorio
 */
public record AvisoDeEvento(long recordatorioId, UUID eventoId, String tituloEvento, Instant inicio, ZoneId zona,
                            boolean esAnuncio) {

    /** Hasta cuantos minutos antes se dice "empieza en N min" en vez de "hoy a las HH:mm". */
    static final long MINUTOS_CUENTA_REGRESIVA = 60;

    private static final String PREFIJO_CLAVE = "recordatorio-evento:";
    private static final Locale ESPANOL = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", ESPANOL);
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ESPANOL);

    public AvisoDeEvento {
        Objects.requireNonNull(eventoId, "eventoId es obligatorio");
        Objects.requireNonNull(inicio, "inicio es obligatorio");
        Objects.requireNonNull(zona, "zona es obligatoria");
        if (tituloEvento == null || tituloEvento.isBlank()) {
            throw new IllegalArgumentException("tituloEvento es obligatorio");
        }
    }

    /**
     * El {@code origen_evento_id} de la notificacion: una por fila de la cola, deterministica.
     * Si el outbox reentrega el mismo evento, la segunda emision choca contra
     * {@code notificaciones_origen_evento_uk} (V16) y se descarta. Mismo recurso que
     * {@code habits.TipoAvisoHabito.claveIdempotencia}.
     */
    public UUID claveDeduplicacion() {
        return UUID.nameUUIDFromBytes((PREFIJO_CLAVE + recordatorioId).getBytes(StandardCharsets.UTF_8));
    }

    /** Adonde lleva el toque. La app instalada al 2026-09-26 no conoce esta ruta y solo se abre. */
    public String rutaApp() {
        return "/eventos/" + eventoId;
    }

    /**
     * Un recordatorio que llega cuando la ocurrencia ya empezo no se manda: diria "empieza en..."
     * de algo que ya paso. Pasa si el backend estuvo caido o si el outbox reintenta muy tarde.
     * Mismo criterio que {@code habits.CalculadoraAvisosHabito}: es preferible no avisar a avisar
     * algo falso. El anuncio de un evento nuevo no tiene ese problema y se manda igual.
     */
    public boolean yaNoSirve(Instant ahora) {
        return !esAnuncio && !ahora.isBefore(inicio);
    }

    public String titulo() {
        return esAnuncio ? "Nuevo evento: " + tituloEvento : tituloEvento;
    }

    public String cuerpo(Instant ahora) {
        String cuando = cuando(ahora);
        return esAnuncio
                ? capitalizar(cuando) + ". Toca para ver el detalle."
                : capitalizar(cuando) + ".";
    }

    private String cuando(Instant ahora) {
        ZonedDateTime inicioLocal = inicio.atZone(zona);
        String hora = HORA.format(inicioLocal);
        long minutos = minutosHastaElInicio(ahora);
        if (!esAnuncio && minutos > 0 && minutos <= MINUTOS_CUENTA_REGRESIVA) {
            return "empieza en " + minutos + " min, a las " + hora;
        }
        LocalDate hoy = ahora.atZone(zona).toLocalDate();
        LocalDate dia = inicioLocal.toLocalDate();
        if (dia.equals(hoy)) {
            return "es hoy a las " + hora;
        }
        if (dia.equals(hoy.plusDays(1))) {
            return "es mañana a las " + hora;
        }
        return "es el " + DIA.format(inicioLocal) + " a las " + hora;
    }

    /** Redondeado hacia arriba: faltando 9 min 30 s se dice "10 min", nunca "9". */
    private long minutosHastaElInicio(Instant ahora) {
        long segundos = Duration.between(ahora, inicio).getSeconds();
        return segundos <= 0 ? 0 : (segundos + 59) / 60;
    }

    private static String capitalizar(String texto) {
        return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
