package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.espiritu.CompletarPastillaRenacerUseCase;
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
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
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

import java.time.Duration;
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
 * <p><b>Espejo hacia el habito "Pastilla Renacer" (resuelto; era la pregunta abierta
 * §10.1/§10.4 de docs/MODULO_HABITS.md):</b> una entrega completa ademas el habito de
 * catalogo {@code PASTILLA_RENACER} de HOY, como en el repo viejo
 * ({@code completePastillaRenacerTrack}, spirit-audio/service.ts). Se hace best-effort y en
 * su PROPIA transaccion — ver {@link #reflejarEnPastillaRenacer}, que explica por que ambas
 * cosas son obligatorias y por que aca REQUIRES_NEW no arriesga el auto-interbloqueo que
 * {@code RegistroService} advierte.
 *
 * <p><b>Audio reproducible:</b> el catalogo dice QUE audio toca; el archivo se sirve como URL
 * prefirmada desde el bucket, igual que la Audioterapia Semanal
 * ({@code AudioterapiaService.firmarAudio}). Ver {@link #firmarAudio} para la degradacion
 * cuando el archivo todavia no esta migrado.
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

    /** Mismo TTL que la Audioterapia Semanal y que la portada de curso — sin motivo para diferir. */
    static final Duration TTL_AUDIO = Duration.ofHours(1);

    private final LoadRegistroEspirituPort loadPort;
    private final SaveRegistroEspirituPort savePort;
    private final AudioCatalogPort audioCatalogPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    /** Firma la URL de lectura del mp3 — mismo puerto y mismo TTL que la Audioterapia Semanal. */
    private final AlmacenamientoPort almacenamientoPort;
    /** Espejo hacia el habito de catalogo, best-effort: ver {@link #reflejarEnPastillaRenacer}. */
    private final CompletarPastillaRenacerUseCase completarPastillaRenacer;
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
                            AlmacenamientoPort almacenamientoPort,
                            CompletarPastillaRenacerUseCase completarPastillaRenacer,
                            Clock clock, IdGenerator idGenerator, PlatformTransactionManager transactionManager) {
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.audioCatalogPort = audioCatalogPort;
        this.progresoPort = progresoPort;
        this.almacenamientoPort = almacenamientoPort;
        this.completarPastillaRenacer = completarPastillaRenacer;
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
        reflejarEnPastillaRenacer(command.actorId(), command.resumenTexto());
        return new ResultadoEntrega(aTiempo);
    }

    /**
     * Completa el habito de catalogo "Pastilla Renacer" de hoy con el mismo resumen — el
     * espejo que el repo viejo hace en {@code completePastillaRenacerTrack}. Sin esto, el
     * aprendiz manda su resumen y el habito le sigue apareciendo pendiente en Training.
     *
     * <p><b>En su PROPIA transaccion (REQUIRES_NEW), no opcional:</b>
     * {@code RegistroService.completar} es {@code @Transactional} (REQUIRED), asi que sin
     * aislarlo se uniria a ESTA transaccion — y cualquier fallo suyo (el track ya expirado, un
     * problema de puntos) marcaria como rollback-only la transaccion que acaba de guardar la
     * entrega del resumen. El aprendiz perderia su texto por un fallo en un habito ajeno.
     *
     * <p><b>Por que aca REQUIRES_NEW es seguro</b> y no cae en el auto-interbloqueo que
     * advierte el javadoc de {@code RegistroService.transaccionPropia}: esa advertencia es
     * sobre abrir una segunda transaccion sobre una fila YA bloqueada por la transaccion en
     * curso. Aca las filas son de tablas distintas — esta transaccion toca
     * {@code registros_espiritu}, la anidada toca {@code registros_habito}, que esta
     * transaccion no leyo ni bloqueo. No hay fila en comun, no hay ciclo posible.
     *
     * <p><b>Best-effort, como en el repo viejo:</b> el resumen ya esta guardado y no se
     * revierte por nada de lo que pase aca. Un fallo se registra y se sigue.
     *
     * <p><b>Se refleja siempre, no solo si la entrega fue a tiempo.</b> El repo viejo solo
     * espejaba las entregas a tiempo, pero en este backend una entrega tardia deja el registro
     * de Espiritu PENDIENTE y el propio {@code RegistroService} ya decide si corresponden
     * puntos segun la ventana del habito ({@code ResultadoOtorgamiento}). Cortar aca por
     * tardanza penalizaria dos veces por lo mismo — una en Espiritu y otra en el habito — y
     * dejaria el habito pendiente para siempre pese a que el aprendiz si escucho y respondio.
     */
    private void reflejarEnPastillaRenacer(UserId participanteId, String resumen) {
        try {
            transaccionPropia.executeWithoutResult(status ->
                    completarPastillaRenacer.completarDeHoy(participanteId, resumen));
        } catch (RuntimeException fallaDelHabitoAjeno) {
            log.warn("[EspirituService] la entrega de {} se guardo, pero no se pudo reflejar en Pastilla Renacer: {}",
                    participanteId, fallaDelHabitoAjeno.toString());
        }
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
        // nada mas: la URL del dia CURRENT ya se firmo dentro de vistaDeUnDia.
        Integer diaActual = tracksPorDia.keySet().stream().max(Integer::compareTo).orElse(null);
        return new EstadoEspiritu(vista, diaActual);
    }

    private DiaEspiritu vistaDeUnDia(int dia, AudioEspiritu audio, RegistroEspiritu track) {
        String titulo = audio != null ? audio.titulo() : null;
        String mime = audio != null ? audio.mime() : null;
        Integer tamano = audio != null ? audio.tamanoBytes() : null;
        if (track == null) {
            return new DiaEspiritu(dia, titulo, "LOCKED", null, null, null, null, null, mime, tamano);
        }
        String estado = switch (track.estado()) {
            case PENDIENTE -> "CURRENT";
            case ENTREGADO -> "SUBMITTED";
            case PERDIDO -> "MISSED";
        };
        // Solo el dia en curso se puede escuchar y entregar: firmar los otros 42 seria trabajo
        // tirado en un endpoint que la app consulta cada vez que abre Training.
        String audioUrl = "CURRENT".equals(estado) ? firmarAudio(audio) : null;
        return new DiaEspiritu(dia, titulo, estado, track.desbloqueadoEn(), track.fechaLimite(), track.entregadoEn(),
                track.resumenTexto(), audioUrl, mime, tamano);
    }

    /**
     * Copia literal del criterio de {@code AudioterapiaService.firmarAudio}: una ruta que ya es
     * una URL absoluta se devuelve tal cual (permite apuntar a un CDN sin tocar codigo), y una
     * ruta de objeto se firma contra el bucket.
     *
     * <p>Devuelve {@code null} cuando el audio de ese dia todavia no tiene archivo servible.
     * Hoy ese es el caso de las 43 filas: los mp3 de Espiritu nunca se migraron del Google
     * Drive viejo al bucket (D-50), asi que {@code audios_espiritu.ruta_storage} esta en NULL
     * (V25). El aprendiz ve el dia, el titulo y el formulario; el reproductor aparece cuando el
     * archivo exista. Se degrada, no se rompe — mismo criterio que el resto del modulo.
     */
    private String firmarAudio(AudioEspiritu audio) {
        if (audio == null || audio.rutaStorage() == null || audio.rutaStorage().isBlank()) {
            return null;
        }
        String ruta = audio.rutaStorage();
        if (ruta.matches("(?i)^https?://.*")) {
            return ruta;
        }
        return almacenamientoPort.firmarLectura(ruta, TTL_AUDIO).toString();
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
