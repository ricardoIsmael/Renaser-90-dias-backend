package com.renaser.os.shared.infrastructure.storage;

import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador por defecto: sin {@code renaser.storage.proveedor=s3} el sistema arranca sin
 * credenciales de AWS y toda URL sale como marcador. Mismo patron que
 * {@code NoOpEnviarEmailAdapter} — el entorno local y los tests no necesitan una cuenta real.
 */
@Component
@ConditionalOnProperty(name = "renaser.storage.proveedor", havingValue = "noop", matchIfMissing = true)
public class NoOpAlmacenamientoAdapter implements AlmacenamientoPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpAlmacenamientoAdapter.class);

    @Override
    public URI firmarSubida(String ruta, String tipoContenido, Duration validez) {
        log.warn("AlmacenamientoPort.firmarSubida({}) placeholder: faltan credenciales AWS S3 (D-34).", ruta);
        return URI.create("about:blank#pendiente-s3/" + ruta);
    }

    @Override
    public URI firmarLectura(String ruta, Duration validez) {
        log.warn("AlmacenamientoPort.firmarLectura({}) placeholder: faltan credenciales AWS S3 (D-34).", ruta);
        return URI.create("about:blank#pendiente-s3/" + ruta);
    }

    @Override
    public void borrar(String ruta) {
        log.warn("AlmacenamientoPort.borrar({}) NO ejecutado de verdad: faltan credenciales AWS S3 (D-34).", ruta);
    }
}
