package com.renaser.os.chat.application.ports.in.mensaje;

import com.renaser.os.chat.domain.model.mensaje.EstadoDeEntrega;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;

/**
 * Proyeccion de lectura de un {@link Mensaje} para el listado (#29): agrega
 * nombre/avatar del emisor y, si responde a otro mensaje, su preview ya resuelto.
 * Ambos se resuelven EN LOTE para la pagina completa — nunca una consulta por mensaje
 * (CLAUDE.MD del encargo). El dominio {@link Mensaje} no cambia: esto es "Full Mapping"
 * de salida (CLAUDE.MD sec. 5.4.1), exclusivo del lado de lectura.
 *
 * <p>{@code mediaUrl} es la URL de lectura ya firmada del adjunto, o {@code null} si el mensaje
 * no lleva media. Se firma en cada lectura y no se guarda: lo persistido es la clave del objeto
 * ({@code mediaRuta}), porque una URL firmada vence y guardarla dejaria la foto en 403 para
 * siempre (mismo criterio y mismo defecto ya cometido en el Muro, E-79). Sin este campo el
 * cliente recibe una clave de S3 que no puede abrir: era el motivo real por el que el chat no
 * podia mostrar fotos ni reproducir audios.
 *
 * <p>{@code estadoDeEntrega} (D-208): ✓ o ✓✓ de un mensaje PROPIO de quien mira; {@code null} en los de
 * otras personas y en los del programa, que no llevan marca. Sale de una sola lectura de los
 * participantes por página ({@code ConsultarLecturaUseCase}), nunca por mensaje.
 *
 * <p>{@code respuestaPreview} (D-251): el resumen del citado, o {@code null} si el mensaje no responde a nada
 * o si lo que citaba ya no se puede mostrar. Las dos cosas se separan con {@link #citaNoDisponible}.
 */
public record MensajeEnriquecido(Mensaje mensaje, String nombreEmisor, String avatarEmisor,
                                  RespuestaPreview respuestaPreview, String mediaUrl,
                                  EstadoDeEntrega estadoDeEntrega) {

    /** Cuantos caracteres del texto original entran en el preview de "respuesta a" —
     * decision propia, no confirmada por producto (ver informe de este encargo). Se cuentan en
     * caracteres Unicode, no en unidades UTF-16: así un emoji nunca queda partido por la mitad (D-251). */
    public static final int LARGO_PREVIEW = 80;

    /** Sin marca de entrega: se la pone después {@link #conEstadoDeEntrega}, cuando es de quien mira. */
    public MensajeEnriquecido(Mensaje mensaje, String nombreEmisor, String avatarEmisor,
                              RespuestaPreview respuestaPreview, String mediaUrl) {
        this(mensaje, nombreEmisor, avatarEmisor, respuestaPreview, mediaUrl, null);
    }

    public MensajeEnriquecido conEstadoDeEntrega(EstadoDeEntrega estado) {
        return new MensajeEnriquecido(mensaje, nombreEmisor, avatarEmisor, respuestaPreview, mediaUrl, estado);
    }

    /**
     * Era una respuesta y lo que citaba ya no se puede mostrar (D-251): su autor lo borró, la moderación lo
     * retiró, o se borró con la cuenta de quien lo escribió. La app dice «Mensaje eliminado».
     */
    public boolean citaNoDisponible() {
        return mensaje.esRespuesta() && respuestaPreview == null;
    }

    /**
     * El resumen del mensaje citado (#29, ampliado en D-251): lo justo para dibujar la cita sin otra llamada.
     *
     * @param previewTexto los primeros {@link #LARGO_PREVIEW} caracteres, en una sola línea; {@code null} si no
     *                     tiene texto (una foto o un audio sin pie)
     * @param mediaMime    para que la app reconozca un sticker igual que en la burbuja (IMAGE + {@code image/webp}
     *                     + el texto «Sticker Renaser: …»)
     * @param mediaUrl     la URL firmada, solo si el citado es una imagen: la miniatura de la cita
     * @param deQuienMira  si el citado lo escribió quien está mirando (la app pone «Tú»)
     */
    public record RespuestaPreview(MensajeId id, String nombreEmisor, TipoMensaje tipo, String previewTexto,
                                    String mediaMime, Short mediaDuracionS, String mediaUrl, boolean deQuienMira) {
    }
}
