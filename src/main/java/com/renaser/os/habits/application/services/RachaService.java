package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.HabitoCompletadoEvent;
import com.renaser.os.habits.api.RachaCompletadaEvent;
import com.renaser.os.habits.application.ports.in.santuario.CerrarRachaUseCase;
import com.renaser.os.habits.application.ports.in.santuario.ExpirarRachasVencidasUseCase;
import com.renaser.os.habits.application.ports.in.santuario.IniciarRachaUseCase;
import com.renaser.os.habits.application.ports.in.santuario.RomperRachaUseCase;
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
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        ExpirarRachasVencidasUseCase {

    /** Habit.systemKey que identifica "Dia sin celular" — nunca por titulo (phoneFreeKeys.ts:12). */
    public static final String CLAVE_SISTEMA_SIN_CELULAR = "PHONE_FREE_DAY";
    /** points.ts HABIT_FULL_POINTS — el ciclo completo puntua fijo, sin pasar por resolveHabitAward. */
    private static final int PUNTOS_CICLO_COMPLETO = 10;
    /** Horas de extension por default para el plazo de cierre (phoneFree.ts usa EXTENSION_WINDOW_HOURS=3). */
    private static final int EXTENSION_DEFAULT_HORAS = 3;

    private final LoadRachaSinCelularPort loadRachaPort;
    private final SaveRachaSinCelularPort saveRachaPort;
    private final LoadRegistroHabitoPort loadRegistroPort;
    private final SaveRegistroHabitoPort saveRegistroPort;
    private final LoadHabitoPort loadHabitoPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final AjustarPuntosPort ajustarPuntosPort;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RachaService(LoadRachaSinCelularPort loadRachaPort, SaveRachaSinCelularPort saveRachaPort,
                         LoadRegistroHabitoPort loadRegistroPort, SaveRegistroHabitoPort saveRegistroPort,
                         LoadHabitoPort loadHabitoPort, ConsultarProgresoParticipanteHabitsPort progresoPort,
                         AjustarPuntosPort ajustarPuntosPort, ApplicationEventPublisher events, Clock clock) {
        this.loadRachaPort = loadRachaPort;
        this.saveRachaPort = saveRachaPort;
        this.loadRegistroPort = loadRegistroPort;
        this.saveRegistroPort = saveRegistroPort;
        this.loadHabitoPort = loadHabitoPort;
        this.progresoPort = progresoPort;
        this.ajustarPuntosPort = ajustarPuntosPort;
        this.events = events;
        this.clock = clock;
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

        RachaSinCelular racha = RachaSinCelular.iniciar(registro.participanteId(), registro.id(),
                command.horasObjetivo(), ahora);
        return saveRachaPort.save(racha);
    }

    @Override
    @Transactional
    public RachaSinCelular cerrar(CerrarRachaCommand command) {
        RachaSinCelular racha = requireRachaActiva(command.actorId());
        Instant ahora = clock.now();

        if (racha.minutosTranscurridos(ahora) < RachaSinCelular.MINUTOS_MINIMOS_CIERRE) {
            throw new IllegalStateException("Todavia no llegas al primer hito de 3 horas");
        }
        if (ahora.isAfter(racha.plazoCierre(EXTENSION_DEFAULT_HORAS))) {
            racha.expirar(ahora);
            saveRachaPort.save(racha);
            liberarRegistro(racha, ahora);
            throw new IllegalStateException("El plazo para cerrar esta racha ya vencio");
        }

        boolean completo = racha.cerrar(ahora);
        RachaSinCelular guardada = saveRachaPort.save(racha);

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
    @Transactional
    public RachaSinCelular romper(RomperRachaCommand command) {
        RachaSinCelular racha = requireRachaActiva(command.actorId());
        Instant ahora = clock.now();
        racha.romper(command.motivo(), ahora);
        RachaSinCelular guardada = saveRachaPort.save(racha);
        liberarRegistro(racha, ahora);
        return guardada;
    }

    @Override
    @Transactional
    public int expirarVencidas(List<UserId> participanteIds) {
        List<RachaSinCelular> activas = loadRachaPort.activasDe(participanteIds);
        Instant ahora = clock.now();
        int expiradas = 0;
        for (RachaSinCelular racha : activas) {
            if (ahora.isAfter(racha.plazoCierre(EXTENSION_DEFAULT_HORAS))) {
                racha.expirar(ahora);
                saveRachaPort.save(racha);
                liberarRegistro(racha, ahora);
                expiradas++;
            }
        }
        return expiradas;
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
