package com.renaser.os.points.application.services;

import com.renaser.os.points.application.ports.in.semaforo.RegistrarSuspensionDelSemaforoUseCase;
import com.renaser.os.points.application.ports.out.semaforo.PausasDelSemaforoPort;
import com.renaser.os.points.domain.model.semaforo.PausaDeMedicion;
import com.renaser.os.points.domain.model.semaforo.PausaId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Anota los días con la cuenta suspendida como un tramo sin medir del semáforo (D-209). La fuente de
 * verdad es el instante del cambio de estado que publica {@code users}, no lo que el barrido alcance a
 * ver: así las fechas salen exactas aunque el evento se procese tarde, y no dependen de que el barrido
 * esté encendido (regla 02 §2: derivar de fechas, no de lo que un cron observa).
 *
 * <p><b>Idempotente y tolerante al orden</b> (el outbox entrega al menos una vez y reintenta cada 5
 * minutos): la suspensión tiene un id determinista sacado del evento, no se abre una segunda mientras
 * hay una en curso, y una reactivación solo cierra una suspensión que empezó antes que ella. Si la
 * suspensión llega DESPUÉS de su reactivación (un reintento), la cuenta ya no está suspendida: se la
 * cierra hoy, lo más tarde que pudo haber terminado, antes que dejar a la persona sin medir para siempre.
 */
@Service
public class SuspensionDelSemaforoService implements RegistrarSuspensionDelSemaforoUseCase {

    private static final Logger log = LoggerFactory.getLogger(SuspensionDelSemaforoService.class);

    private final ProgramasActivadosFinder programasFinder;
    private final UserSummaryFinder userSummaryFinder;
    private final PausasDelSemaforoPort pausasPort;
    private final Clock clock;

    public SuspensionDelSemaforoService(ProgramasActivadosFinder programasFinder, UserSummaryFinder userSummaryFinder,
                                        PausasDelSemaforoPort pausasPort, Clock clock) {
        this.programasFinder = programasFinder;
        this.userSummaryFinder = userSummaryFinder;
        this.pausasPort = pausasPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void alSuspender(UserId usuario, Instant instante) {
        Optional<ZoneId> zona = zonaDe(usuario);
        if (zona.isEmpty()) {
            return;   // sin programa activado el semáforo no la mide: no hay nada que anotar
        }
        PausaId id = idDeLaSuspension(usuario, instante);
        if (pausasDe(usuario).stream().anyMatch(p -> p.id().equals(id) || p.suspensionEnCurso())) {
            return;   // ya anotada: una reentrega del outbox, o la del relleno de V72
        }
        PausaDeMedicion suspension = PausaDeMedicion.porSuspension(id, usuario, diaLocal(instante, zona.get()), instante);
        if (!sigueSuspendida(usuario)) {
            cerrarHoy(suspension, zona.get());
        }
        pausasPort.guardar(suspension);
    }

    @Override
    @Transactional
    public void alReactivar(UserId usuario, Instant instante) {
        Optional<ZoneId> zona = zonaDe(usuario);
        if (zona.isEmpty()) {
            return;
        }
        pausasDe(usuario).stream()
                .filter(PausaDeMedicion::suspensionEnCurso)
                .filter(suspension -> !suspension.creadaEn().isAfter(instante))
                .findFirst()
                .ifPresent(suspension -> {
                    suspension.terminarSuspension(diaLocal(instante, zona.get()), instante);
                    pausasPort.guardar(suspension);
                });
    }

    /**
     * El mismo evento da siempre el mismo id. Se trunca a milisegundos para que una reentrega leída del
     * outbox (JSON) dé el mismo id que la primera entrega, aunque la serialización recorte los nanos.
     */
    static PausaId idDeLaSuspension(UserId usuario, Instant instante) {
        String clave = "semaforo-suspension|" + usuario.value() + "|" + instante.truncatedTo(ChronoUnit.MILLIS);
        return PausaId.of(UUID.nameUUIDFromBytes(clave.getBytes(StandardCharsets.UTF_8)));
    }

    /** La reactivación ya pasó y se procesó antes que esta suspensión: se cierra hoy, en su zona. */
    private void cerrarHoy(PausaDeMedicion suspension, ZoneId zona) {
        Instant ahora = clock.now();
        LocalDate hoy = diaLocal(ahora, zona);
        log.warn("[points.SuspensionDelSemaforo] la suspension de {} del {} llego con la cuenta ya reactivada; "
                + "se la cierra hoy ({})", suspension.usuarioId(), suspension.desde(), hoy);
        suspension.terminarSuspension(hoy, ahora);
    }

    private boolean sigueSuspendida(UserId usuario) {
        return userSummaryFinder.findById(usuario).map(u -> u.status() == UserStatus.SUSPENDED).orElse(false);
    }

    private Optional<ZoneId> zonaDe(UserId usuario) {
        return programasFinder.de(usuario).map(ProgramaActivado::zona);
    }

    private List<PausaDeMedicion> pausasDe(UserId usuario) {
        return pausasPort.de(List.of(usuario)).getOrDefault(usuario, List.of());
    }

    /** El día de la persona, nunca el del servidor (regla 02 §1). */
    private static LocalDate diaLocal(Instant instante, ZoneId zona) {
        return instante.atZone(zona).toLocalDate();
    }
}
