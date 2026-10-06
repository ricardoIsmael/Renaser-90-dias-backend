package com.renaser.os.calendar.application.ports.in.asistencia;

import com.renaser.os.calendar.domain.model.asistencia.CierreDeLista;
import com.renaser.os.calendar.domain.model.asistencia.EstadoAsistencia;
import com.renaser.os.calendar.domain.model.asistencia.VentanaDePasarLista;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/**
 * Pasar lista en una ocurrencia (D-256): ver la lista, marcar a alguien (A tiempo / Tarde / sin marcar =
 * ausente), cerrarla y reabrirla para corregir. Solo seguimiento: no da puntos ni toca coherencia, semáforo
 * ni racha. Mismas personas autorizadas que {@link VerRespuestasDelEventoUseCase}.
 */
public interface PasarListaUseCase {

    ListaDeAsistencia ver(UserId actor, EventoId eventoId, Instant inicioOcurrencia);

    /** Idempotente: repetir el mismo estado no cambia nada; {@code estado == null} quita la marca. */
    PersonaConvocada marcar(MarcarAsistenciaCommand comando);

    /** Idempotente: cerrar una lista cerrada la devuelve como está. */
    ListaDeAsistencia cerrar(UserId actor, EventoId eventoId, Instant inicioOcurrencia);

    /** Idempotente: reabrir una lista abierta la devuelve como está. */
    ListaDeAsistencia reabrir(UserId actor, EventoId eventoId, Instant inicioOcurrencia);

    record MarcarAsistenciaCommand(UserId actor, EventoId eventoId, Instant inicioOcurrencia, UserId persona,
                                   EstadoAsistencia estado) {
    }

    /**
     * @param abiertaAhora     si ahora se puede marcar: la ventana está abierta y la lista no está cerrada
     * @param cierre           {@code null} = la lista no está cerrada
     * @param cerradaPorNombre nombre de quien la cerró, si se sabe
     */
    record ListaDeAsistencia(Instant inicioOcurrencia, VentanaDePasarLista ventana, boolean abiertaAhora,
                             CierreDeLista cierre, String cerradaPorNombre, List<PersonaConvocada> personas) {
    }
}
