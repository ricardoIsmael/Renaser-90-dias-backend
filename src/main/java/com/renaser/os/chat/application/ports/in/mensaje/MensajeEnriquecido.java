package com.renaser.os.chat.application.ports.in.mensaje;

import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;

import java.time.Instant;

/**
 * Proyeccion de lectura de un {@link Mensaje} para el listado (#29): agrega
 * nombre/avatar del emisor y, si responde a otro mensaje, su preview ya resuelto.
 * Ambos se resuelven EN LOTE para la pagina completa — nunca una consulta por mensaje
 * (CLAUDE.MD del encargo). El dominio {@link Mensaje} no cambia: esto es "Full Mapping"
 * de salida (CLAUDE.MD sec. 5.4.1), exclusivo del lado de lectura.
 */
public record MensajeEnriquecido(Mensaje mensaje, String nombreEmisor, String avatarEmisor,
                                  RespuestaPreview respuestaPreview) {

    /** Cuantos caracteres del texto original entran en el preview de "respuesta a" —
     * decision propia, no confirmada por producto (ver informe de este encargo). */
    public static final int LARGO_PREVIEW = 80;

    /** {@code eliminadoEn} espeja el tombstone del mensaje original (hoy siempre
     * {@code null}: no existe todavia un caso de uso que borre mensajes — ver
     * {@link Mensaje}). Se incluye igual para que el frontend no necesite otro campo el
     * dia que ese caso de uso exista. */
    public record RespuestaPreview(MensajeId id, String nombreEmisor, TipoMensaje tipo, String previewTexto,
                                    Instant eliminadoEn) {
    }
}
