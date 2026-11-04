package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.meet.AuditoriaGooglePort;
import com.renaser.os.calendar.domain.model.meet.AccionDeAuditoriaGoogle;
import com.renaser.os.calendar.domain.model.meet.RegistroDeAuditoriaGoogle;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Deja la fila de auditoría de cada cosa que se hace con la cuenta de Google (spec 040, FR-016). Auditar nunca rompe lo que se estaba haciendo: si la
 * fila no se puede escribir, se avisa en el registro (sin datos de la operación) y se sigue.
 */
@Component
class AuditoriaDeGoogle {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaDeGoogle.class);

    private final AuditoriaGooglePort auditoriaPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    AuditoriaDeGoogle(AuditoriaGooglePort auditoriaPort, Clock clock, IdGenerator idGenerator) {
        this.auditoriaPort = auditoriaPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /** Una correlación nueva para una operación que va a dejar varias filas. */
    String nuevaCorrelacion() {
        return idGenerator.newId().toString();
    }

    void registrar(AccionDeAuditoriaGoogle accion, UserId actor, UUID eventoId, boolean exito, Integer codigoHttp,
                   String correlacion) {
        try {
            auditoriaPort.registrar(new RegistroDeAuditoriaGoogle(idGenerator.newId(), clock.now(), accion, actor, eventoId,
                    exito, codigoHttp, correlacion));
        } catch (RuntimeException ex) {
            log.warn("[calendar.AuditoriaDeGoogle] no se pudo escribir la auditoria de {}: {}", accion,
                    ex.getClass().getSimpleName());
        }
    }
}
