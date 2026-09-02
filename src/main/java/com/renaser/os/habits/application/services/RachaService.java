package com.renaser.os.habits.application.services;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort;
import com.renaser.os.evidence.api.RegistrarEvidenciaPort.RegistrarEvidenciaComando;
import com.renaser.os.habits.api.HabitoCompletadoEvent;
import com.renaser.os.habits.api.RachaCompletadaEvent;
import com.renaser.os.habits.application.ports.in.santuario.CerrarRachaUseCase;
import com.renaser.os.habits.application.ports.in.santuario.ExpirarRachasVencidasUseCase;
import com.renaser.os.habits.application.ports.in.santuario.IniciarRachaUseCase;
import com.renaser.os.habits.application.ports.in.santuario.RomperRachaUseCase;
import com.renaser.os.habits.application.ports.in.santuario.SolicitarUrlAdjuntoRachaUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.santuario.LoadRachaSinCelularPort;
import com.renaser.os.habits.application.ports.out.santuario.SaveRachaSinCelularPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelular;
import com.renaser.os.habits.domain.model.santuario.RachaSinCelularId;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * "Dia sin celular" — traduccion 1:1 de `phoneFree.ts`+`phoneFreeLadder.ts`
 * (repo viejo, paso 0 en docs/MODULO_HABITS.md). Honor-based: a diferencia de
 * {@link SantuarioService}, romper una racha NUNCA penaliza puntos (phoneFree.ts:23-28).
 * Solo el ciclo completo de 24h otorga {@code PUNTOS_COMPLETOS_HABITO} puntos fijos
 * (medido por duracion, no por puntualidad — NUNCA pasa por
 * {@link com.renaser.os.habits.domain.model.registro.ResultadoOtorgamiento}).
 */
@Service
public class RachaService implements IniciarRachaUseCase, CerrarRachaUseCase, RomperRachaUseCase,
        SolicitarUrlAdjuntoRachaUseCase, ExpirarRachasVencidasUseCase {

    private static final Logger log = LoggerFactory.getLogger(RachaService.class);

    /** Habit.systemKey que identifica "Dia sin celular" — nunca por titulo (phoneFreeKeys.ts:12). */
    public static final String CLAVE_SISTEMA_SIN_CELULAR = "PHONE_FREE_DAY";
    /** points.ts HABIT_FULL_POINTS — el ciclo completo puntua fijo, sin pasar por resolveHabitAward. */
    private static final int PUNTOS_CICLO_COMPLETO = 10;
    /** Horas de extension por default para el plazo de cierre (phoneFree.ts usa EXTENSION_WINDOW_HOURS=3). */
    private static final int EXTENSION_DEFAULT_HORAS = 3;
    /** Bucket propio de evidencia (D-34), mismo patron que `rocks`/`calendar`/`support`. */
    static final String BUCKET_DIA_SIN_CELULAR = "renaser-files";
    private static final String PREFIJO_RUTA = "dia-sin-celular";
    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(10);

    private final LoadRachaSinCelularPort loadRachaPort;
    private final SaveRachaSinCelularPort saveRachaPort;
    private final LoadRegistroHabitoPort loadRegistroPort;
    private final SaveRegistroHabitoPort saveRegistroPort;
    private final LoadHabitoPort loadHabitoPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final AjustarPuntosPort ajustarPuntosPort;
    private final RegistrarEvidenciaPort registrarEvidenciaPort;
    private final AlmacenamientoPort almacenamientoPort;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final IdGenerator idGenerator;
    /**
     * Transaccion PROPIA (REQUIRES_NEW) para el barrido nocturno de {@link #expirarVencidas}
     * — misma razon y misma advertencia que {@code RegistroService.transaccionPropia}: solo
     * para el barrido, nunca para {@link #cerrar} (ahi C-9 se resuelve con {@code
     * noRollbackFor}, porque la racha ya esta bajo bloqueo pesimista de la transaccion en
     * curso — ver {@link #requireRachaActiva}).
     */
    private final TransactionTemplate transaccionPropia;

    public RachaService(LoadRachaSinCelularPort loadRachaPort, SaveRachaSinCelularPort saveRachaPort,
                         LoadRegistroHabitoPort loadRegistroPort, SaveRegistroHabitoPort saveRegistroPort,
                         LoadHabitoPort loadHabitoPort, ConsultarProgresoParticipanteHabitsPort progresoPort,
                         AjustarPuntosPort ajustarPuntosPort, RegistrarEvidenciaPort registrarEvidenciaPort,
                         AlmacenamientoPort almacenamientoPort, ApplicationEventPublisher events, Clock clock,
                         IdGenerator idGenerator, PlatformTransactionManager transactionManager) {
        this.loadRachaPort = loadRachaPort;
        this.saveRachaPort = saveRachaPort;
        this.loadRegistroPort = loadRegistroPort;
        this.saveRegistroPort = saveRegistroPort;
        this.loadHabitoPort = loadHabitoPort;
        this.progresoPort = progresoPort;
        this.ajustarPuntosPort = ajustarPuntosPort;
        this.registrarEvidenciaPort = registrarEvidenciaPort;
        this.almacenamientoPort = almacenamientoPort;
        this.events = events;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    @Override
    @Transactional
    public RachaSinCelular iniciar(IniciarRachaCommand command) {
        RegistroHabito registro = requireRegistro(command.registroId());
        requireSelf(command.actorId(), registro.participanteId());
        Habito habito = requireHabito(registro);
        if (!esHabitoSinCelular(habito)) {
            throw new IllegalArgumentException("Este habito no lleva rachas sin celular");
        }
        requireProgreso(registro.participanteId());

        if (loadRachaPort.activaDe(registro.participanteId()).isPresent()) {
            throw new IllegalStateException("Ya tienes una racha en curso — cierrala o rompela antes de empezar otra");
        }

        Instant ahora = clock.now();
        registro.iniciar(ahora);
        saveRegistroPort.save(registro);

        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD 5.4.7).
        RachaSinCelular racha = RachaSinCelular.iniciar(RachaSinCelularId.of(idGenerator.newId()),
                registro.participanteId(), registro.id(), command.horasObjetivo(), ahora);
        return saveRachaPort.save(racha);
    }

    @Override
    @Transactional(noRollbackFor = RachaVencidaException.class)
    public RachaSinCelular cerrar(CerrarRachaCommand command) {
        RachaSinCelular racha = requireRachaActiva(command.actorId());
        Instant ahora = clock.now();

        if (racha.minutosTranscurridos(ahora) < RachaSinCelular.MINUTOS_MINIMOS_CIERRE) {
            throw new IllegalStateException("Todavia no llegas al primer hito de 3 horas");
        }
        if (ahora.isAfter(racha.plazoCierre(EXTENSION_DEFAULT_HORAS))) {
            // C-9: igual que en RegistroService.completar — sin el noRollbackFor de arriba
            // (acotado a este tipo puntual, no a IllegalStateException en general) el throw
            // revierte el save y liberarRegistro de las lineas anteriores, la racha queda
            // ACTIVA para siempre y rachas_viva_uk le impide al aprendiz iniciar otra.
            racha.expirar(ahora);
            saveRachaPort.save(racha);
            liberarRegistro(racha, ahora);
            throw new RachaVencidaException("El plazo para cerrar esta racha ya vencio");
        }

        boolean completo = racha.cerrar(ahora);
        RachaSinCelular guardada = saveRachaPort.save(racha);

        // La evidencia se cuelga del registro en que ARRANCO la racha, no del de hoy — es
        // el que se completa y el que lleva los puntos (mismo criterio que phoneFree.ts).
        registrarEvidenciaPort.registrar(new RegistrarEvidenciaComando(racha.participanteId(),
                new DestinoEvidencia.RegistroHabito(racha.registroHabitoId().value()), command.tipoEvidencia(),
                command.bucket(), command.rutaStorage(), command.contenidoTexto(), command.timestampExif(), null,
                null, false, ahora));

        if (completo) {
            RegistroHabito registro = requireRegistro(racha.registroHabitoId());
            registro.completar(PUNTOS_CICLO_COMPLETO, null, null, null, ahora);
            saveRegistroPort.save(registro);
            ajustarPuntosPort.ajustar(racha.participanteId(), MotivoPuntos.HABIT_COMPLETED, PUNTOS_CICLO_COMPLETO,
                    "Dia sin celular — ciclo de 24h");
            events.publishEvent(new RachaCompletadaEvent(racha.id().value(), racha.participanteId(), ahora));
            events.publishEvent(new HabitoCompletadoEvent(registro.id().value(), registro.participanteId(),
                    registro.habitoId().value(), PUNTOS_CICLO_COMPLETO, ahora));
        } else {
            liberarRegistro(racha, ahora);
        }
        return guardada;
    }

    @Override
    public UrlAdjuntoRacha solicitarUrl(SolicitarUrlAdjuntoRachaCommand command) {
        requireProgreso(command.actorId());
        RachaSinCelular racha = loadRachaPort.activaDe(command.actorId())
                .orElseThrow(() -> new NoSuchElementException("No tienes ninguna racha en curso"));
        String ruta = PREFIJO_RUTA + "/" + command.actorId() + "/" + racha.id();
        URI url = almacenamientoPort.firmarSubida(ruta, command.tipoContenido(), VALIDEZ_URL_SUBIDA);
        return new UrlAdjuntoRacha(url, BUCKET_DIA_SIN_CELULAR, ruta);
    }

    @Override
    @Transactional
    public RachaSinCelular romper(RomperRachaCommand command) {
        RachaSinCelular racha = requireRachaActiva(command.actorId());
        Instant ahora = clock.now();
        racha.romper(command.motivo(), ahora);
        RachaSinCelular guardada = saveRachaPort.save(racha);
        liberarRegistro(racha, ahora);
        return guardada;
    }

    /**
     * C-6: cada racha vencida se cierra en su propia transaccion ({@link #transaccionPropia},
     * REQUIRES_NEW) — si una falla (por ejemplo, {@link #liberarRegistro} no encuentra el
     * registro), esa racha queda ACTIVA para el proximo barrido y las demas se procesan
     * igual, en vez de que una sola fila mala revierta el barrido completo de la noche.
     */
    @Override
    public int expirarVencidas(List<UserId> participanteIds) {
        List<RachaSinCelular> activas = loadRachaPort.activasDe(participanteIds);
        Instant ahora = clock.now();
        int expiradas = 0;
        int fallidas = 0;
        for (RachaSinCelular racha : activas) {
            if (!ahora.isAfter(racha.plazoCierre(EXTENSION_DEFAULT_HORAS))) {
                continue;
            }
            try {
                expirarUnaEnTransaccionPropia(racha, ahora);
                expiradas++;
            } catch (RuntimeException ex) {
                fallidas++;
                log.warn("[habits] no se pudo expirar la racha sin celular {} (participante {}): {}", racha.id(),
                        racha.participanteId(), ex.toString());
            }
        }
        if (expiradas > 0 || fallidas > 0) {
            log.info("[habits] barrido de expiracion de rachas sin celular: {} expirada(s), {} fallida(s) de {} activa(s)",
                    expiradas, fallidas, activas.size());
        }
        return expiradas;
    }

    private void expirarUnaEnTransaccionPropia(RachaSinCelular racha, Instant ahora) {
        transaccionPropia.executeWithoutResult(status -> {
            racha.expirar(ahora);
            saveRachaPort.save(racha);
            liberarRegistro(racha, ahora);
        });
    }

    private void liberarRegistro(RachaSinCelular racha, Instant ahora) {
        RegistroHabito registro = requireRegistro(racha.registroHabitoId());
        boolean esDeHoy = registro.fechaEjecucion().equals(fechaHoyDe(registro.participanteId()));
        registro.liberar(esDeHoy, ahora);
        saveRegistroPort.save(registro);
    }

    /** Usa el reloj inyectado (nunca el reloj real del sistema — CLAUDE.MD §5.4.7, testeable sin esperar). */
    private LocalDate fechaHoyDe(UserId participanteId) {
        String timezone = progresoPort.deParticipante(participanteId)
                .map(ProgresoParticipanteHabits::timezone).orElse("UTC");
        return clock.now().atZone(ZoneId.of(timezone)).toLocalDate();
    }

    private boolean esHabitoSinCelular(Habito habito) {
        return CLAVE_SISTEMA_SIN_CELULAR.equals(habito.claveSistema());
    }

    private void requireSelf(UserId actorId, UserId participanteId) {
        if (!actorId.equals(participanteId)) {
            throw new NotAuthorizedException("Solo el propio participante puede operar su racha sin celular");
        }
    }

    private RachaSinCelular requireRachaActiva(UserId actorId) {
        requireProgreso(actorId);
        return loadRachaPort.activaDeParaEscritura(actorId)
                .orElseThrow(() -> new NoSuchElementException("No tienes ninguna racha en curso"));
    }

    private RegistroHabito requireRegistro(RegistroHabitoId id) {
        return loadRegistroPort.byId(id)
                .orElseThrow(() -> new NoSuchElementException("Registro no encontrado: " + id));
    }

    private Habito requireHabito(RegistroHabito registro) {
        return loadHabitoPort.byId(registro.habitoId())
                .orElseThrow(() -> new NoSuchElementException("Habito no encontrado: " + registro.habitoId()));
    }

    private ProgresoParticipanteHabits requireProgreso(UserId participanteId) {
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return progreso;
    }
}
