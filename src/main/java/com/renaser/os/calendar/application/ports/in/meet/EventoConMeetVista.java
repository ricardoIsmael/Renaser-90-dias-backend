package com.renaser.os.calendar.application.ports.in.meet;

import com.renaser.os.calendar.application.ports.in.evento.EventoVista;

/** Un evento y, si quien pregunta administra el calendario y el evento pide Meet, cómo van sus salas ({@code null} en cualquier otro caso). */
public record EventoConMeetVista(EventoVista evento, ResumenDeMeet meet) {
}
