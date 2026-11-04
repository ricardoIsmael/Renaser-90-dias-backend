package com.renaser.os.calendar.application.ports.in.evento;

import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.Evento;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

public interface ListarEventosParaVisorUseCase {

    /** Rango maximo de 90 dias — EventRangeQuery del repo viejo (schema.ts). */
    int RANGO_MAXIMO_DIAS = 90;

    /** Los eventos de la formación de quien pide (spec 024), sin elegir ninguna otra. */
    default List<OcurrenciaVista> listar(UserId actorId, Instant desde, Instant hasta) {
        return listar(actorId, desde, hasta, null);
    }

    /**
     * {@code formacion}: solo Administración, Alquimista y Soporte TI eligen una (los demás leen la suya); sin elegir, «Todas», cada evento con la
     * suya. Pedir una que no se ve da una lista vacía.
     */
    List<OcurrenciaVista> listar(UserId actorId, Instant desde, Instant hasta, Formacion formacion);

    /**
     * {@code ubicacionDeMeet}: solo en un evento con el enlace de Meet automático (spec 040, FR-017). Cada fecha tiene su sala y su enlace, así que
     * el de la fecha reemplaza al del evento (que es el de la próxima); {@code null} = el evento no es de Meet automático y vale el suyo.
     */
    record OcurrenciaVista(Evento evento, String coverUrl, Instant inicioOcurrencia, Instant iniciaEn,
                            Integer duracionMinutos, String titulo, EstadoConfirmacion viewerRsvpStatus,
                            EnlaceDeLaFecha ubicacionDeMeet) {

        public OcurrenciaVista(Evento evento, String coverUrl, Instant inicioOcurrencia, Instant iniciaEn,
                               Integer duracionMinutos, String titulo, EstadoConfirmacion viewerRsvpStatus) {
            this(evento, coverUrl, inicioOcurrencia, iniciaEn, duracionMinutos, titulo, viewerRsvpStatus, null);
        }

        public OcurrenciaVista conEnlace(EnlaceDeLaFecha enlace) {
            return new OcurrenciaVista(evento, coverUrl, inicioOcurrencia, iniciaEn, duracionMinutos, titulo,
                    viewerRsvpStatus, enlace);
        }
    }

    /** El enlace de Meet de ESA fecha; {@code enlace} es nulo si su sala todavía no existe (no se muestra el de otra fecha). */
    record EnlaceDeLaFecha(String enlace) {
    }
}
