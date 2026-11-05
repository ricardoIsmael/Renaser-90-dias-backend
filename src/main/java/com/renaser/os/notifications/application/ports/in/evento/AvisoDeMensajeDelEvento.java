package com.renaser.os.notifications.application.ports.in.evento;

import com.renaser.os.notifications.domain.model.evento.AvisoDeEvento;
import com.renaser.os.notifications.domain.model.evento.MensajeDeEvento;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Un aviso de MENSAJE de un evento para una persona (spec 041), ya desarmado del evento de {@code calendar} que lo trajo: el chat y el WhatsApp lo
 * reciben igual. {@code dijoVoy} se leyo al despachar, no al programar.
 *
 * @param recordatorioId el id de la fila de la cola de recordatorios; es la clave de deduplicacion de todo lo que sale de aca
 * @param avisoMin       cuantos minutos antes del inicio es este aviso (1440, 60, 30 o 10)
 * @param formacion      el codigo de la formacion del evento ({@code ESCUELA}, {@code DIPLOMADO}, {@code CERTIFICACION}); {@code null} = la Escuela
 * @param enlace         el enlace para entrar si el evento es en linea, o {@code null}
 * @param lugar          la direccion si es presencial, o {@code null}
 */
public record AvisoDeMensajeDelEvento(long recordatorioId, UUID eventoId, UserId destinatarioId, Instant inicio, String zonaHoraria,
                                      String titulo, int avisoMin, boolean dijoVoy, String formacion, String enlace, String lugar) {

    /** El mismo aviso que ve la app (el texto de «cuando» vive en un solo lugar), no un anuncio de evento nuevo. */
    public AvisoDeEvento avisoDeEvento() {
        return new AvisoDeEvento(recordatorioId, eventoId, titulo, inicio, ZoneId.of(zonaHoraria), false);
    }

    /** Lo que se dice: el texto del chat y los huecos de la plantilla de WhatsApp. */
    public MensajeDeEvento mensaje() {
        return new MensajeDeEvento(avisoDeEvento(), enlace, lugar);
    }
}
