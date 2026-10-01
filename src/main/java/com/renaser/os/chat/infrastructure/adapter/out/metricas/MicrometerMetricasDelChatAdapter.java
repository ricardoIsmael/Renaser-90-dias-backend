package com.renaser.os.chat.infrastructure.adapter.out.metricas;

import com.renaser.os.chat.application.ports.out.metricas.RegistrarMetricaDelChatPort;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * En Prometheus (D-237): {@code renaser_chat_media_subidas_solicitadas_total{tipo}} y
 * {@code renaser_chat_media_enviada_total{tipo}}, con {@code tipo} imagen, audio o video.
 */
@Component
class MicrometerMetricasDelChatAdapter implements RegistrarMetricaDelChatPort {

    private static final Logger log = LoggerFactory.getLogger(MicrometerMetricasDelChatAdapter.class);

    private final MeterRegistry registro;

    MicrometerMetricasDelChatAdapter(MeterRegistry registro) {
        this.registro = registro;
    }

    @Override
    public void subidaDeMediaSolicitada(TipoMensaje tipo) {
        contar("renaser.chat.media.subidas.solicitadas", "URLs firmadas para subir media al chat", tipo);
    }

    @Override
    public void mediaEnviada(TipoMensaje tipo) {
        contar("renaser.chat.media.enviada", "Mensajes del chat con un archivo subido", tipo);
    }

    /** Medir nunca rompe el envio: una metrica perdida no puede costar un mensaje. */
    private void contar(String nombre, String descripcion, TipoMensaje tipo) {
        try {
            Counter.builder(nombre).description(descripcion)
                    .tag("tipo", tipo.name().toLowerCase(Locale.ROOT))
                    .register(registro).increment();
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar una metrica del chat ({})", e.getClass().getSimpleName());
        }
    }
}
