package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.grabacionv90.ProcesarValidacionV90UseCase;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.LoadGrabacionV90Port;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.SaveGrabacionV90Port;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.ValidacionIAPort;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.ValidacionIAPort.ResultadoValidacionV90;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.ValidacionIAPort.SolicitudValidacionV90;
import com.renaser.os.onboarding.domain.model.grabacionv90.EstadoIAv90;
import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    /**
     * Invocado desde el hilo @Async de {@code DespacharValidacionV90Port} — hace el trabajo real.
     *
     * <p><b>C-1 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html), CRÍTICO:</b>
     * este método YA NO es {@code @Transactional}. La versión vieja envolvía la lectura, la
     * llamada a la IA (hasta 45s con Gemini real, CLAUDE.MD §7) y el guardado en una sola
     * transacción — con varios aprendices validando a la vez, cada hilo retenía una conexión
     * de Hikari mientras esperaba a Gemini y agotaba el pool para TODA la API (login, hábitos,
     * chat), no solo para onboarding. Ahora: la lectura y la escritura corren cada una en su
     * propia transacción corta, automática, la que ya provee Spring Data JPA en
     * {@code GrabacionV90PersistenceAdapter} (no hace falta declarar una acá — declararla
     * volvería a envolver todo el método, mismo bug de nuevo); la llamada a la IA queda en
     * el medio, sin ninguna transacción abierta.
     */
    @Override
    public void procesar(UserId usuarioId, long grabacionId) {
        GrabacionV90 grabacion = loadGrabacionPort.porId(grabacionId).orElse(null);
        if (grabacion == null || !grabacion.usuarioId().equals(usuarioId)) {
            log.warn("[onboarding.ProcesarValidacionV90Service] procesar: grabacion {} inexistente o de otro "
                    + "usuario, se ignora", grabacionId);
            return;
        }
        ResultadoValidacionV90 resultado = validarSinDejarAtrapada(usuarioId, grabacionId, grabacion);
        try {
            aplicarResultadoYGuardar(grabacion, resultado);
        } catch (RuntimeException e) {
            log.error("[onboarding.ProcesarValidacionV90Service] fallo aplicando/guardando el veredicto de la "
                    + "grabacion {}: {}", grabacionId, e.getMessage(), e);
            rescatarDeProcesandoEterno(grabacionId);
        }
    }

    private void aplicarResultadoYGuardar(GrabacionV90 grabacion, ResultadoValidacionV90 resultado) {
        switch (resultado.estado()) {
            case APROBADA -> grabacion.registrarAprobacion(resultado.feedbackJson(), clock);
            case RECHAZADA -> grabacion.registrarRechazo(resultado.feedbackJson(), clock);
            case NO_DISPONIBLE -> grabacion.registrarSinResultado(clock);
        }
        saveGrabacionPort.guardar(grabacion);
    }

    /**
     * C-3 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): si aplicar el
     * veredicto o guardarlo falla (ej. Postgres se cae justo en ese momento, o el guard de
     * dominio rechaza un estado inesperado), el objeto en memoria ya mutó pero ese cambio
     * puede no haber llegado a Postgres — hay que releer lo que hay REALMENTE comprometido en
     * base, no reusar el objeto en memoria (que ya no refleja con certeza lo persistido).
     * Solo se fuerza a {@code PENDIENTE}/{@code REVISION_MANUAL} si la base todavia ve
     * {@code PROCESANDO}: si por el contrario el guardado si llego a buen puerto y lo que
     * fallo fue algo posterior, no hay nada que rescatar y no se pisa un veredicto valido.
     * Se trata igual que "IA no disponible": la maquina de estados de {@link GrabacionV90} ya
     * sabe reintentar o caer a revision manual, no hace falta un estado nuevo.
     */
    private void rescatarDeProcesandoEterno(long grabacionId) {
        try {
            loadGrabacionPort.porId(grabacionId).ifPresent(actual -> {
                if (actual.estadoIa() == EstadoIAv90.PROCESANDO) {
                    actual.registrarSinResultado(clock);
                    saveGrabacionPort.guardar(actual);
                }
            });
        } catch (RuntimeException e) {
            log.error("[onboarding.ProcesarValidacionV90Service] no se pudo rescatar la grabacion {} de "
                    + "PROCESANDO tras un fallo previo: {}", grabacionId, e.getMessage(), e);
        }
    }

    /**
     * Con el {@code NoOpValidacionIAAdapter} de hoy {@link ValidacionIAPort#validar} nunca
     * lanza. Con Gemini real sí puede (timeout, error de red) — y como ya no hay una
     * transacción envolvente que se revierta sola, un fallo sin capturar dejaría a
     * {@link #procesar} sin llegar nunca a {@code saveGrabacionPort.guardar}, y la grabación
     * quedaría en {@code PROCESANDO} para siempre (el mismo síntoma que describe C-3 del
     * informe de auditoría). Se trata igual que {@code NO_DISPONIBLE}: la máquina de estados
     * de {@link GrabacionV90} ya sabe reintentar o caer a {@code REVISION_MANUAL}
     * (CLAUDE.MD §7) — no hay que inventar un camino nuevo, alcanza con no perder el fallo.
     */
    private ResultadoValidacionV90 validarSinDejarAtrapada(UserId usuarioId, long grabacionId,
                                                            GrabacionV90 grabacion) {
        try {
            return validacionIAPort.validar(new SolicitudValidacionV90(usuarioId, grabacionId, grabacion.fase(),
                    grabacion.eje(), grabacion.transcripcion()));
        } catch (RuntimeException e) {
            log.warn("[onboarding.ProcesarValidacionV90Service] la IA fallo validando la grabacion {}: {}",
                    grabacionId, e.getMessage(), e);
            return ResultadoValidacionV90.noDisponible();
        }
    }
}
