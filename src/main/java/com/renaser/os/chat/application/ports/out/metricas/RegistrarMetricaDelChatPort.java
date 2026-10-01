package com.renaser.os.chat.application.ports.out.metricas;

import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;

/**
 * Cuanta foto, audio y video se sube al chat, para los paneles de Grafana (D-237). Solo el tipo:
 * ni la conversacion ni quien. Una subida tiene dos momentos y se cuentan los dos: la URL firmada
 * (empieza) y el mensaje que la usa (termino); la diferencia son subidas abandonadas o fallidas.
 */
public interface RegistrarMetricaDelChatPort {

    void subidaDeMediaSolicitada(TipoMensaje tipo);

    /** Un mensaje con un archivo subido por la persona (no cuenta lo compartido del Muro). */
    void mediaEnviada(TipoMensaje tipo);
}
