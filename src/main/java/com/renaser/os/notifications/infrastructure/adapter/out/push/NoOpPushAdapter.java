package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.renaser.os.notifications.application.ports.out.push.PushPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Placeholder sin credenciales Expo reales — mismo patron que
 * {@code shared/infrastructure/storage/NoOpAlmacenamientoAdapter} (D-34): solo loguea, para que
 * el resto del modulo (dominio, casos de uso, listeners de evento) quede completo y probado
 * detras de {@link PushPort} sin bloquearse en una integracion externa que todavia no existe.
 * Cuando haya credenciales Expo, este adaptador se reemplaza por uno real (HTTP a
 * {@code https://exp.host/--/api/v2/push/send}, mismo endpoint que ya usaba el repo viejo) sin
 * tocar ni el dominio ni los casos de uso.
 */
@Component
public class NoOpPushAdapter implements PushPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpPushAdapter.class);

    @Override
    public void enviar(List<String> tokens, String titulo, String cuerpo) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        // Sin contenido en el log (CLAUDE.MD §5.4.9): titulo/cuerpo pueden llevar datos
        // personales (ej. MENSAJE_MENTOR), solo se deja constancia de que "algo" se intento enviar.
        log.info("[notifications.NoOpPushAdapter] push simulado a {} token(s)", tokens.size());
    }
}
