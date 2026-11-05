package com.renaser.os.chat.api;

import com.renaser.os.shared.domain.UserId;

/**
 * Lo que {@code notifications} necesita para que el recordatorio de un evento tambien aparezca en el chat de soporte de la persona (spec 041,
 * FR-001): un mensaje del programa, firmado con el nombre de SU formacion ({@code «Diplomado Renaser»} en el soporte del Diplomado), igual que
 * la tarjeta del semaforo, la Caja y la emergencia. Lo resuelve {@code chat}, que es quien sabe cual es el soporte vigente de cada aprendiz.
 *
 * <p>La dependencia va de {@code notifications} hacia {@code chat}, como {@link AvisosDeMensajesFinder}; {@code chat} no conoce a
 * {@code notifications} (FR-006). <b>No genera un segundo push</b> (FR-002): el aviso de la app ya fue el push; el mensaje solo queda en el
 * chat y cuenta como no leido.
 */
public interface RecordatorioDeEventoEnElChat {

    /**
     * Escribe el recordatorio una sola vez: el mensaje lleva un id calculado a partir de {@code recordatorioId}, asi que la reentrega del
     * *outbox* no lo duplica ni lo pierde.
     *
     * @param persona        a quien se le recuerda; se escribe en SU chat de soporte, el de la formacion en que esta hoy
     * @param recordatorioId el id de la fila de la cola de recordatorios; es la clave de deduplicacion
     * @param texto          lo que dice el mensaje (titulo, cuando y enlace), ya armado por quien llama
     */
    Resultado escribir(UserId persona, long recordatorioId, String texto);

    enum Resultado {
        /** El mensaje salio ahora. */
        ESCRITO,
        /** Ya estaba escrito (una reentrega): no se repite. */
        YA_ESTABA,
        /** La persona no tiene chat de soporte (por ejemplo el personal): no se le crea uno. */
        SIN_CHAT_DE_SOPORTE,
        /** Soporte TI apago el chat de soporte: no se escribe nada. */
        CHAT_APAGADO
    }
}
