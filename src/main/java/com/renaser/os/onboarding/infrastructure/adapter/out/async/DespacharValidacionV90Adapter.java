package com.renaser.os.onboarding.infrastructure.adapter.out.async;

import com.renaser.os.onboarding.application.ports.in.grabacionv90.ProcesarValidacionV90UseCase;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.DespacharValidacionV90Port;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * {@code @Async} necesita ser un bean DISTINTO del que lo llama (la auto-invocacion salta
 * el proxy de Spring) — por eso este adaptador es una clase aparte que solo reenvia a
 * {@link ProcesarValidacionV90UseCase}, en vez de que el propio servicio se llame a si
 * mismo.
 */
@Component
class DespacharValidacionV90Adapter implements DespacharValidacionV90Port {

    private static final Logger log = LoggerFactory.getLogger(DespacharValidacionV90Adapter.class);

    private final ProcesarValidacionV90UseCase procesarUseCase;

    DespacharValidacionV90Adapter(ProcesarValidacionV90UseCase procesarUseCase) {
        this.procesarUseCase = procesarUseCase;
    }

    @Override
    @Async
    public void despachar(UserId usuarioId, long grabacionId) {
        try {
            procesarUseCase.procesar(usuarioId, grabacionId);
        } catch (RuntimeException e) {
            log.error("[onboarding.DespacharValidacionV90Adapter] fallo procesando validacion de grabacion {}: {}",
                    grabacionId, e.getMessage(), e);
        }
    }
}
