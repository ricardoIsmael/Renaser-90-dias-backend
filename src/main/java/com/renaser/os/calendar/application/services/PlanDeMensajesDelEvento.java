package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.calendar.domain.model.recordatorio.CalculadoraRecordatorios;
import com.renaser.os.calendar.domain.model.recordatorio.InstanteDeMensaje;
import com.renaser.os.calendar.domain.model.recordatorio.RecordatorioEvento;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Los avisos de MENSAJE de un evento (spec 041, FR-014): el recordatorio que sale por el chat de soporte y por WhatsApp. {@code calendar} decide
 * QUE y CUANDO (1 dia, 1 hora, 30 y 10 minutos antes, por defecto) y los deja en la misma cola {@code recordatorios_evento} que los avisos del
 * telefono; {@code notifications} decide COMO y a QUIEN. Son independientes de las reglas del aviso del telefono: ni las cambian ni las leen.
 *
 * <p>Las antelaciones salen de la configuracion ({@code renaser.eventos.mensajes.antelaciones-min}): cambiarlas no exige codigo ni migracion.
 * Una lista vacia apaga estos avisos sin tocar los del telefono.
 */
@Component
class PlanDeMensajesDelEvento {

    private final List<Integer> antelacionesMin;
    private final Clock clock;

    PlanDeMensajesDelEvento(@Value("${renaser.eventos.mensajes.antelaciones-min:1440,60,30,10}") List<Integer> antelacionesMin,
                            Clock clock) {
        this.antelacionesMin = List.copyOf(antelacionesMin);
        this.clock = clock;
    }

    boolean hayAvisos() {
        return antelacionesMin.stream().anyMatch(m -> m != null && m > 0);
    }

    /** Cuantos dias por delante hay que mirar para que el aviso mas lejano se genere a tiempo, nunca menos de {@code minimo}. */
    int diasDeVentana(int minimo) {
        return CalculadoraRecordatorios.diasDeVentanaDeMensaje(antelacionesMin, minimo);
    }

    /** Los instantes de una ocurrencia que todavia llegan a tiempo; los que ya pasaron cuando se creo o se edito el evento no se generan. */
    List<InstanteDeMensaje> instantes(Instant inicioOcurrencia, Instant ahora) {
        return CalculadoraRecordatorios.instantesDeMensaje(inicioOcurrencia, antelacionesMin, ahora);
    }

    /** Una fila de la cola por persona e instante. */
    List<RecordatorioEvento> filas(Evento evento, List<InstanteDeMensaje> instantes, List<UserId> usuarios) {
        return usuarios.stream()
                .flatMap(usuario -> instantes.stream().map(instante -> RecordatorioEvento.programarMensaje(evento.id(),
                        instante.inicioOcurrencia(), usuario, instante.enviarEn(), instante.minutosAntes(), clock)))
                .toList();
    }
}
