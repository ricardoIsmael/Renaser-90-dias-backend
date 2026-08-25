package com.renaser.os.academy.infrastructure.adapter.out.ia;

import com.renaser.os.academy.application.ports.out.recomendacion.RecomendarClasePort;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Placeholder sin integracion de IA real — mismo patron que
 * `shared/infrastructure/storage/NoOpAlmacenamientoAdapter` (D-34) y
 * `notifications/.../out/push/NoOpPushAdapter`: Academia Adaptativa es Ola 5
 * (CLAUDE.MD, checklist §12). Solo loguea y devuelve vacio, para que el caso
 * de uso de recomendacion diaria quede completo y probado detras del puerto
 * sin bloquearse en Spring AI / Gemini, que todavia no estan configurados
 * (ver exclusion de auto-config en application.yaml).
 */
@Component
public class NoOpRecomendarClaseAdapter implements RecomendarClasePort {

    private static final Logger log = LoggerFactory.getLogger(NoOpRecomendarClaseAdapter.class);

    @Override
    public Optional<ClaseRecomendada> recomendar(UserId participanteId) {
        log.warn("RecomendarClasePort.recomendar({}) placeholder: Academia Adaptativa (IA) es Ola 5.", participanteId);
        return Optional.empty();
    }
}
