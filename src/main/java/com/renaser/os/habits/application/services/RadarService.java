package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarUltimoRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.radar.LoadMiRadarPort;
import com.renaser.os.habits.application.ports.out.radar.LoadRegistroRadarPort;
import com.renaser.os.habits.application.ports.out.radar.SaveRegistroRadarPort;
import com.renaser.os.habits.domain.model.radar.HorarioDelRadar;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadarId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.OptionalInt;

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
 *
 * <p>Spec 012 (2026-10-29): desde el dia 8 del programa el Codigo Renaser es Mi Radar —opcional, con las horas que la
 * persona elige para todos los dias o para cada dia de la semana— y {@link #registrar} aplica {@code HorarioDelRadar}
 * con el momento LOCAL de la persona (su hora y su dia de la semana): los dias 0 a 7 no cambian. La autorizacion (la
 * propia persona, con el programa andando) vive en {@code AccesoAlRadar}.
 */
@Service
public class RadarService implements RegistrarCheckInRadarUseCase, ConsultarUltimoRadarUseCase,
        ConsultarHistorialRadarUseCase {

    private final AccesoAlRadar acceso;
    private final LoadRegistroRadarPort loadPort;
    private final SaveRegistroRadarPort savePort;
    private final LoadMiRadarPort miRadarPort;
    private final LecturaDelRadar lectura;
    private final Clock clock;
    private final IdGenerator idGenerator;

    RadarService(AccesoAlRadar acceso, LoadRegistroRadarPort loadPort, SaveRegistroRadarPort savePort,
                 LoadMiRadarPort miRadarPort, LecturaDelRadar lectura, Clock clock, IdGenerator idGenerator) {
        this.acceso = acceso;
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.miRadarPort = miRadarPort;
        this.lectura = lectura;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public RegistroRadar registrar(RegistrarCheckInRadarCommand command) {
        ProgresoParticipanteHabits progreso = acceso.propio(command.actorId(), command.participanteId());
        Instant ahora = clock.now();
        /* Spec 012: la hora y el dia de la semana son los de la persona (su zona, regla 02), no los del servidor:
           con el reloj en la madrugada UTC el dia local es el anterior. Los dias 0 a 7 siempre se puede registrar,
           como hoy; desde el dia 8 solo con Mi Radar encendido, en un dia que tenga Radar y de 03:00 a 23:59. Es una
           regla de ESTADO y no de permisos: 409 y no 403, porque en la app un 403 apaga el Radar entero. */
        MiRadar miRadar = miRadarPort.deParticipante(command.participanteId());
        ZonedDateTime local = ahora.atZone(ZoneId.of(progreso.timezone()));
        requirePuedeRegistrar(progreso.diaPrograma(), miRadar, local);
        /* Un Codigo Renaser por hora: si ya hay uno en la franja en curso, se devuelve ESE en vez
           de crear otro.

           El Codigo Renaser dejo de ser un log libre cuando paso a tener doce franjas fijas (una
           por hora, dias 1 a 7): dos filas en la misma hora no son dos registros, son el mismo
           duplicado por un doble toque o por dos dispositivos a la vez. Devolver el existente hace
           que reintentar sea inofensivo — el cliente recibe un 200 con el registro de esa hora y
           cierra el formulario, que es exactamente lo que la persona espera.

           Esto es una COMPROBACION, no una garantia: entre leer y escribir hay una ventana. La
           garantia la pone el indice unico de V52. */
        Optional<RegistroRadar> deEstaHora = loadPort.ultimoDeParticipante(command.participanteId())
                .filter(previo -> mismaHora(previo.creadoEn(), ahora));
        if (deEstaHora.isPresent()) {
            return deEstaHora.get();
        }
        OptionalInt prevista = HorarioDelRadar.horaPrevista(progreso.diaPrograma(), miRadar, local);
        return savePort.save(nuevoRegistro(command, ahora, prevista));
    }

    private static void requirePuedeRegistrar(int diaPrograma, MiRadar miRadar, ZonedDateTime local) {
        if (HorarioDelRadar.puedeRegistrar(diaPrograma, miRadar, local)) {
            return;
        }
        throw new IllegalStateException(motivoDelRechazo(miRadar, local.getDayOfWeek()));
    }

    private static String motivoDelRechazo(MiRadar miRadar, DayOfWeek diaDeLaSemana) {
        if (!miRadar.estaEncendido()) {
            return "Mi Radar está apagado";
        }
        return miRadar.horasDelDia(diaDeLaSemana).isEmpty()
                ? "Hoy no tienes Mi Radar" : "Mi Radar funciona de 03:00 a 23:59";
    }

    private RegistroRadar nuevoRegistro(RegistrarCheckInRadarCommand command, Instant ahora, OptionalInt prevista) {
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD 5.4.7).
        RegistroRadar registro = RegistroRadar.registrar(RegistroRadarId.of(idGenerator.newId()),
                command.participanteId(), command.queHago(), command.quePienso(), command.queSiento(),
                command.nivelEnergia(), command.queEvito(), ahora);
        return prevista.isPresent() ? registro.conHoraPrevista(prevista.getAsInt()) : registro;
    }

    /**
     * Misma franja horaria. Se trunca en UTC igual que el indice unico de V52 — no en la zona del
     * participante — para que la comprobacion y la garantia partan la hora en el mismo instante.
     * Que sea UTC no cambia nada en Lima, que es UTC-5 exacto.
     *
     * <p>Package-private desde 2026-09-23: {@code DiarioYRadarService} le avisa al acompanante si la
     * franja en curso ya esta ocupada, y tiene que partir la hora exactamente igual que aca.
     */
    static boolean mismaHora(Instant a, Instant b) {
        return a.truncatedTo(ChronoUnit.HOURS).equals(b.truncatedTo(ChronoUnit.HOURS));
    }

    @Override
    public Optional<RegistroRadar> ultimo(UserId actorId, UserId participanteId) {
        acceso.propio(actorId, participanteId);
        return loadPort.ultimoDeParticipante(participanteId);
    }

    @Override
    public HistorialRadarPage historial(UserId actorId, UserId participanteId, Instant cursor, int tamanoPagina) {
        acceso.propio(actorId, participanteId);
        return lectura.pagina(participanteId, cursor, tamanoPagina);
    }
}
