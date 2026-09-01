package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarUltimoRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.application.ports.out.radar.LoadRegistroRadarPort;
import com.renaser.os.habits.application.ports.out.radar.SaveRegistroRadarPort;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadarId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Codigo Renaser (`RegistroRadar`) — traduccion del check-in que hoy escribe
 * directo a Supabase desde el cliente (D-41, docs/MODULO_HABITS.md §radar
 * paso 0). El backend viejo SI tenia esta regla en servidor (RC-01/RC-02,
 * `src/app/api/v1/radar/route.ts` + `src/features/daily-checkin/service.ts`,
 * repo clonado en Backend90dias) — el cliente actual la saltea escribiendo
 * directo a Postgres via Supabase, no porque la regla nunca haya existido.
 *
 * <p>Restriccion de rol a TRAINEE tomada literal de ese contrato viejo
 * (`requireRole(auth.data, ['TRAINEE'])` en ambas rutas) — el Codigo Renaser
 * nunca fue self-service para otros roles.
 */
@Service
public class RadarService implements RegistrarCheckInRadarUseCase, ConsultarUltimoRadarUseCase,
        ConsultarHistorialRadarUseCase {

    private final LoadRegistroRadarPort loadPort;
    private final SaveRegistroRadarPort savePort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public RadarService(LoadRegistroRadarPort loadPort, SaveRegistroRadarPort savePort,
                         ConsultarProgresoParticipanteHabitsPort progresoPort, Clock clock,
                         IdGenerator idGenerator) {
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.progresoPort = progresoPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public RegistroRadar registrar(RegistrarCheckInRadarCommand command) {
        requireSelf(command.actorId(), command.participanteId());
        requireParticipanteHabilitado(command.participanteId());
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD 5.4.7).
        RegistroRadar registro = RegistroRadar.registrar(RegistroRadarId.of(idGenerator.newId()),
                command.participanteId(), command.queHago(), command.quePienso(), command.queSiento(),
                command.nivelEnergia(), command.queEvito(), clock.now());
        return savePort.save(registro);
    }

    @Override
    public Optional<RegistroRadar> ultimo(UserId actorId, UserId participanteId) {
        requireSelf(actorId, participanteId);
        requireParticipanteHabilitado(participanteId);
        return loadPort.ultimoDeParticipante(participanteId);
    }

    @Override
    public HistorialRadarPage historial(UserId actorId, UserId participanteId, Instant cursor, int tamanoPagina) {
        requireSelf(actorId, participanteId);
        requireParticipanteHabilitado(participanteId);
        List<RegistroRadar> pagina = loadPort.historialDeParticipante(participanteId, cursor, tamanoPagina);
        Instant siguienteCursor = siguienteCursor(pagina, tamanoPagina);
        return new HistorialRadarPage(pagina, siguienteCursor);
    }

    /** Pagina llena => puede haber mas, misma heuristica que radar.ts:384-387 (sin conteo extra). */
    private static Instant siguienteCursor(List<RegistroRadar> pagina, int tamanoPagina) {
        if (pagina.size() < tamanoPagina) {
            return null;
        }
        return pagina.get(pagina.size() - 1).creadoEn();
    }

    private void requireSelf(UserId actorId, UserId participanteId) {
        if (!actorId.equals(participanteId)) {
            throw new NotAuthorizedException("Solo el propio participante puede operar su Codigo Renaser");
        }
    }

    private void requireParticipanteHabilitado(UserId participanteId) {
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (progreso.rol() != RolParticipante.TRAINEE) {
            throw new NotAuthorizedException("El Codigo Renaser es exclusivo de aprendices");
        }
    }
}
