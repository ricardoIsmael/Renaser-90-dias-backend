package com.renaser.os.calendar.application.ports.out.meet;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.meet.SalaMeet;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface LoadSalaMeetPort {

    List<SalaMeet> porEvento(EventoId eventoId);

    Map<EventoId, List<SalaMeet>> porEventos(Set<EventoId> eventoIds);

    /** Los eventos con alguna sala a la que le toca volver a hablar con Google, para el proceso en segundo plano. */
    List<EventoId> eventosConTrabajoPendiente(Instant ahora, int limite);

    /**
     * Los eventos vivos que pidieron Meet y todavía tienen fechas por delante (los de un solo día, desde ayer; los que se repiten, hasta que
     * termine su serie): el proceso horario les vuelve a calcular las salas, así las fechas que entraron a la ventana reciben la suya.
     */
    List<EventoId> eventosConSalaAutomatica(Instant ahora);

    /**
     * Los eventos con salas creadas que todavía faltan por pasar a la Meet API o con la sala vieja por cerrar (spec 040 FR-041), de fechas de
     * {@code desde} en adelante, los de fecha más cercana primero. Las pasadas no se tocan.
     */
    List<EventoId> eventosConSalasPorMigrar(java.time.LocalDate desde, int limite);
}
