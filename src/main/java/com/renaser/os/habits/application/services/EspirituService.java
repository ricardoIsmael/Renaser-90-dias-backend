package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase;
import com.renaser.os.habits.application.ports.out.espiritu.AudioCatalogPort;
import com.renaser.os.habits.application.ports.out.espiritu.AudioCatalogPort.AudioEspiritu;
import com.renaser.os.habits.application.ports.out.espiritu.LoadRegistroEspirituPort;
import com.renaser.os.habits.application.ports.out.espiritu.SaveRegistroEspirituPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.espiritu.EstadoRegistroEspiritu;
import com.renaser.os.habits.domain.model.espiritu.RegistroEspiritu;
import com.renaser.os.habits.domain.model.espiritu.RegistroEspirituId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeSet;

/**
 * "Espiritu" — audioterapia diaria (tabla {@code registros_espiritu}). Traduccion 1:1 del
 * state machine lazy de {@code spirit-audio/service.ts} (repo viejo, paso 0 del encargo):
 * {@code ensureAdvanced} se llama en cada lectura de estado y en cada entrega, nunca por un
 * cron dedicado — auto-sanador, sin importar cuando el aprendiz abre la app.
 *
 * <p><b>Constantes (verificadas contra el repo viejo, no inventadas):</b>
 * {@code UNLOCK_HOUR=7}, {@code DEADLINE_HOUR=12}, {@code AUDIO_UNLOCK_START_DAY=7}
 * (audio N se desbloquea en diaPrograma N+7). Una entrega tardia deja el track PENDIENTE
 * (nunca lanza) — recien el proximo chequeo lazy con {@code daysSince>=1} lo pasa a
 * PERDIDO, con exactamente un dia de bloqueo despues (repo viejo: "the 1-day lockout").
 *
 * <p><b>Fuera de alcance de esta pasada (decision explicita, CLAUDE.MD §0.6):</b> el
 * espejo hacia el habito "Pastilla Renacer" que el repo viejo hace como efecto secundario
 * best-effort de una entrega a tiempo ({@code completePastillaRenacerTrack},
 * spirit-audio/service.ts) NO se replico aca. No fue parte del encargo explicito de este
 * agregado y, a diferencia del repo viejo (Prisma, sin el mismo riesgo), hacerlo bien en
 * Spring exige aislar esa escritura en su propia transaccion (REQUIRES_NEW) para no
 * arriesgar marcar la transaccion principal como rollback-only ante un fallo de un habito
 * ajeno — se documenta como pregunta abierta en docs/MODULO_HABITS.md en vez de
 * improvisarlo.
 */
@Service
public class EspirituService implements ConsultarEstadoEspirituUseCase, EntregarResumenEspirituUseCase {

    private static final Logger log = LoggerFactory.getLogger(EspirituService.class);

    /** Hora local desde la que se evalua el avance del dia (spirit-audio/service.ts:55). */
    static final LocalTime HORA_DESBLOQUEO = LocalTime.of(7, 0);
    /** Hora limite de entrega, mismo dia del desbloqueo (spirit-audio/service.ts:56). */
    static final LocalTime HORA_LIMITE = LocalTime.of(12, 0);
    /** audioDay = diaPrograma - AUDIO_UNLOCK_START_DAY; diaPrograma 8 -> audio 1 (confirmado con cliente 2026-08-10). */
    static final int AUDIO_UNLOCK_START_DAY = 7;

    private final LoadRegistroEspirituPort loadPort;
    private final SaveRegistroEspirituPort savePort;
    private final AudioCatalogPort audioCatalogPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final Clock clock;
    private final IdGenerator idGenerator;
    /**
     * Transaccion PROPIA (REQUIRES_NEW) para el INSERT de {@link #crearSiHayAudio} — C-10
     * (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html). Mismo criterio que
     * {@code RegistroService}/{@code RachaService}/{@code PromocionCambioHorarioService}: no
     * se toca ninguna fila bloqueada por la transaccion en curso (esto es un INSERT de una
     * fila nueva, no una actualizacion de una ya leida), asi que aislarla no arriesga un
     * auto-interbloqueo.
     */
    private final TransactionTemplate transaccionPropia;

    public EspirituService(LoadRegistroEspirituPort loadPort, SaveRegistroEspirituPort savePort,
                            AudioCatalogPort audioCatalogPort, ConsultarProgresoParticipanteHabitsPort progresoPort,
                            Clock clock, IdGenerator idGenerator, PlatformTransactionManager transactionManager) {
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.audioCatalogPort = audioCatalogPort;
        this.progresoPort = progresoPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    @Override
    @Transactional
    public EstadoEspiritu consultar(UserId actorId) {
        ProgresoParticipanteHabits progreso = requireParticipanteHabilitado(actorId);
        ZoneId zona = ZoneId.of(progreso.timezone());
        Instant ahora = clock.now();
        asegurarAvance(actorId, progreso.diaPrograma(), zona, ahora);
        return construirVista(actorId);
    }

    @Override
    @Transactional
    public ResultadoEntrega entregar(EntregarResumenEspirituCommand command) {
        ProgresoParticipanteHabits progreso = requireParticipanteHabilitado(command.actorId());
        ZoneId zona = ZoneId.of(progreso.timezone());
        Instant ahora = clock.now();
        asegurarAvance(command.actorId(), progreso.diaPrograma(), zona, ahora);

        RegistroEspiritu registro = loadPort.porParticipanteYDia(command.actorId(), command.dia())
                .orElseThrow(() -> new NoSuchElementException("Ese dia no esta desbloqueado"));
        if (registro.estado() != EstadoRegistroEspiritu.PENDIENTE) {
            throw new IllegalStateException("Ya se registro este dia (enviado o vencido): " + registro.estado());
        }
        boolean aTiempo = registro.entregar(command.resumenTexto(), ahora);
        savePort.save(registro);
        return new ResultadoEntrega(aTiempo);
    }

    // ─── State machine lazy (ensureAdvanced, spirit-audio/service.ts:127-173) ──────────────

    private void asegurarAvance(UserId participanteId, int diaPrograma, ZoneId zona, Instant ahora) {
        if (ahora.atZone(zona).toLocalTime().isBefore(HORA_DESBLOQUEO)) {
            return;
        }
        Optional<RegistroEspiritu> ultimo = loadPort.ultimoDe(participanteId);
        if (ultimo.isEmpty()) {
            desbloquearPrimerAudio(participanteId, diaPrograma, zona, ahora);
            return;
        }
        avanzarDesde(participanteId, ultimo.get(), zona, ahora);
    }

    private void desbloquearPrimerAudio(UserId participanteId, int diaPrograma, ZoneId zona, Instant ahora) {
        int diaAudio = diaPrograma - AUDIO_UNLOCK_START_DAY;
        if (diaAudio < 1) {
            return;
        }
        crearSiHayAudio(participanteId, diaAudio, zona, ahora);
    }

    private void avanzarDesde(UserId participanteId, RegistroEspiritu ultimo, ZoneId zona, Instant ahora) {
        LocalDate hoy = ahora.atZone(zona).toLocalDate();
        LocalDate diaDelUltimo = diaCalendarioDe(ultimo, zona);
        long diasDesde = ChronoUnit.DAYS.between(diaDelUltimo, hoy);
        if (diasDesde <= 0) {
            return; // el ultimo track ya es de hoy: nada que avanzar
        }

        EstadoRegistroEspiritu estado = ultimo.estado();
        if (estado == EstadoRegistroEspiritu.PENDIENTE) {
            ultimo.marcarPerdido(ahora);
            savePort.save(ultimo);
            estado = EstadoRegistroEspiritu.PERDIDO;
        }
        // Exactamente un ciclo de bloqueo: el dia siguiente a un PERDIDO queda bloqueado.
        if (estado == EstadoRegistroEspiritu.PERDIDO && diasDesde == 1) {
            return;
        }
        crearSiHayAudio(participanteId, ultimo.dia() + 1, zona, ahora);
    }

    /**
     * C-10 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): {@code consultar}
     * es una lectura que, si hace falta, desbloquea el siguiente audio escribiendo una fila
     * nueva — un clasico check-then-act. Dos lecturas casi simultaneas del mismo participante
     * (la app movil reintenta, o el usuario refresca dos veces) pueden ver el mismo "ultimo"
     * registro y las dos decidir que hay que desbloquear el MISMO dia: las dos intentan el
     * INSERT y la segunda pierde contra el {@code UNIQUE(participante_id, dia)} — sin este
     * arreglo, {@code GlobalExceptionHandler} traduce eso a un 409 para una simple lectura.
     *
     * <p>El INSERT corre en su propia transaccion ({@link #transaccionPropia}, REQUIRES_NEW)
     * a proposito: si se atrapara la violacion de unicidad dentro de la MISMA transaccion de
     * {@code consultar}/{@code entregar} (ambos {@code @Transactional}), Postgres ya dejo esa
     * transaccion abortada en cuanto el INSERT fallo — cualquier lectura posterior en la misma
     * transaccion (como {@link #construirVista} o {@code porParticipanteYDia}) explotaria con
     * "current transaction is aborted" en vez de con el error real. Aislando el INSERT, si
     * pierde la carrera solo se deshace ESA transaccion chica; la de {@code consultar}/
     * {@code entregar} sigue sana y puede releer con normalidad.
     *
     * <p>No hace falta releer la fila ganadora aca: quien pierde la carrera no necesita el
     * {@code RegistroEspiritu} recien creado por el otro hilo, solo necesita no fallar — los
     * llamadores ({@link #construirVista}, {@code porParticipanteYDia} en {@code entregar})
     * ya releen el estado fresco de la base despues de {@code asegurarAvance}, asi que ambos
     * terminan viendo la MISMA fila (la del hilo que gano), sea cual sea el orden real de
     * llegada.
     */
    private void crearSiHayAudio(UserId participanteId, int diaAudio, ZoneId zona, Instant ahora) {
        if (audioCatalogPort.porDia(diaAudio).isEmpty()) {
            return; // catalogo no tiene ese dia todavia: el aprendiz queda al dia, esperando contenido
        }
        Instant fechaLimite = ahora.atZone(zona).toLocalDate().atTime(HORA_LIMITE).atZone(zona).toInstant();
        RegistroEspiritu nuevo = RegistroEspiritu.desbloquear(RegistroEspirituId.of(idGenerator.newId()),
                participanteId, diaAudio, ahora, fechaLimite, ahora);
        try {
            transaccionPropia.executeWithoutResult(status -> savePort.save(nuevo));
        } catch (DataIntegrityViolationException yaDesbloqueadoPorOtraLectura) {
            log.debug("[EspirituService] el dia {} de {} ya fue desbloqueado por una lectura concurrente",
                    diaAudio, participanteId);
        }
    }

    /** El "dia" calendario al que pertenece un track es el de su plazo de entrega (mediodia). */
    private static LocalDate diaCalendarioDe(RegistroEspiritu registro, ZoneId zona) {
        return registro.fechaLimite().atZone(zona).toLocalDate();
    }

    // ─── Vista de lectura ───────────────────────────────────────────────────────────────────

    private EstadoEspiritu construirVista(UserId participanteId) {
        List<RegistroEspiritu> tracks = loadPort.todosDe(participanteId);
        Map<Integer, RegistroEspiritu> tracksPorDia = new LinkedHashMap<>();
        for (RegistroEspiritu track : tracks) {
            tracksPorDia.put(track.dia(), track);
        }
        Map<Integer, AudioEspiritu> catalogoPorDia = new LinkedHashMap<>();
        for (AudioEspiritu audio : audioCatalogPort.todos()) {
            catalogoPorDia.put(audio.dia(), audio);
        }

        TreeSet<Integer> dias = new TreeSet<>();
        dias.addAll(catalogoPorDia.keySet());
        dias.addAll(tracksPorDia.keySet());

        List<DiaEspiritu> vista = new ArrayList<>();
        for (int dia : dias) {
            vista.add(vistaDeUnDia(dia, catalogoPorDia.get(dia), tracksPorDia.get(dia)));
        }
        Integer diaActual = tracksPorDia.keySet().stream().max(Integer::compareTo).orElse(null);
        return new EstadoEspiritu(vista, diaActual);
    }

    private static DiaEspiritu vistaDeUnDia(int dia, AudioEspiritu audio, RegistroEspiritu track) {
        String titulo = audio != null ? audio.titulo() : null;
        if (track == null) {
            return new DiaEspiritu(dia, titulo, "LOCKED", null, null, null, null);
        }
        String estado = switch (track.estado()) {
            case PENDIENTE -> "CURRENT";
            case ENTREGADO -> "SUBMITTED";
            case PERDIDO -> "MISSED";
        };
        return new DiaEspiritu(dia, titulo, estado, track.desbloqueadoEn(), track.fechaLimite(), track.entregadoEn(),
                track.resumenTexto());
    }

    private ProgresoParticipanteHabits requireParticipanteHabilitado(UserId actorId) {
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(actorId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + actorId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        if (progreso.rol() != RolParticipante.TRAINEE) {
            throw new NotAuthorizedException("Espiritu es exclusivo de aprendices");
        }
        return progreso;
    }
}
