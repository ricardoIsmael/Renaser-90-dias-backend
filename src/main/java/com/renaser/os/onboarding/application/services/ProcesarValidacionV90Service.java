package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.grabacionv90.ProcesarValidacionV90UseCase;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.LoadGrabacionV90Port;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.SaveGrabacionV90Port;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.ValidacionIAPort;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.ValidacionIAPort.ResultadoValidacionV90;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.ValidacionIAPort.SolicitudValidacionV90;
import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Separado de {@link GrabacionV90Service} a proposito (E-34, {@code docs/BITACORA_ERRORES.md}):
 * {@code DespacharValidacionV90Adapter} (@Async) necesita invocar este caso de uso, y
 * {@link GrabacionV90Service} necesita el puerto que dispara ese adapter — si el mismo
 * servicio implementara los dos lados, Spring no puede crear ninguno de los dos beans
 * (dependencia circular: servicio → puerto → adapter → servicio).
 */
@Service
class ProcesarValidacionV90Service implements ProcesarValidacionV90UseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcesarValidacionV90Service.class);

    private final LoadGrabacionV90Port loadGrabacionPort;
    private final SaveGrabacionV90Port saveGrabacionPort;
    private final ValidacionIAPort validacionIAPort;
    private final Clock clock;

    ProcesarValidacionV90Service(LoadGrabacionV90Port loadGrabacionPort, SaveGrabacionV90Port saveGrabacionPort,
                                  ValidacionIAPort validacionIAPort, Clock clock) {
        this.loadGrabacionPort = loadGrabacionPort;
        this.saveGrabacionPort = saveGrabacionPort;
        this.validacionIAPort = validacionIAPort;
        this.clock = clock;
    }

    /** Invocado desde el hilo @Async de {@code DespacharValidacionV90Port} — hace el trabajo real. */
    @Override
    @Transactional
    public void procesar(UserId usuarioId, long grabacionId) {
        GrabacionV90 grabacion = loadGrabacionPort.porId(grabacionId).orElse(null);
        if (grabacion == null || !grabacion.usuarioId().equals(usuarioId)) {
            log.warn("[onboarding.ProcesarValidacionV90Service] procesar: grabacion {} inexistente o de otro "
                    + "usuario, se ignora", grabacionId);
            return;
        }
        ResultadoValidacionV90 resultado = validacionIAPort.validar(
                new SolicitudValidacionV90(usuarioId, grabacionId, grabacion.fase(), grabacion.eje(),
                        grabacion.transcripcion()));
        switch (resultado.estado()) {
            case APROBADA -> grabacion.registrarAprobacion(resultado.feedbackJson(), clock);
            case RECHAZADA -> grabacion.registrarRechazo(resultado.feedbackJson(), clock);
            case NO_DISPONIBLE -> grabacion.registrarSinResultado(clock);
        }
        saveGrabacionPort.guardar(grabacion);
    }
}
