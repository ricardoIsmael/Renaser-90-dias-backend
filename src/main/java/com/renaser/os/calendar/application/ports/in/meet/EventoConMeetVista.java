package com.renaser.os.calendar.application.ports.in.meet;

import com.renaser.os.calendar.application.ports.in.evento.EventoVista;

/**
 * El evento con cómo van sus salas de Meet (spec 040, solo para quien lo gestiona) y si la sesión se graba o se transcribe (spec 047, decisión P4-B
 * del dueño: lo ve TODO el que ve el evento, para avisarle antes de entrar).
 */
public record EventoConMeetVista(EventoVista evento, ResumenDeMeet meet, boolean seGrabaOTranscribe) {

    public EventoConMeetVista(EventoVista evento, ResumenDeMeet meet) {
        this(evento, meet, false);
    }
}
