package com.renaser.os.chat.application.ports.in.lectura;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;

/**
 * Avisa en vivo hasta dónde leyeron todos, para que quien escribió vea pasar ✓ a ✓✓ sin recargar
 * (D-208). Lo llama {@code ConversacionService.marcarLeido} DESPUÉS de guardar la lectura, y solo
 * después de autorizarla: el aviso sale por {@code /topic/conversaciones/{id}}, que ya filtra quién lo
 * recibe al suscribirse y en cada entrega.
 *
 * <p>Nunca falla hacia arriba: la lectura ya quedó guardada, y un aviso que no sale solo significa que
 * la marca se verá al volver a abrir el chat.
 */
@FunctionalInterface
public interface AnunciarLecturaUseCase {

    void anunciar(Conversacion conversacion);
}
