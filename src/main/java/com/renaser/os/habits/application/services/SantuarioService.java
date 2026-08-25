package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.HabitoCompletadoEvent;
import com.renaser.os.habits.api.SantuarioRotoEvent;
import com.renaser.os.habits.application.ports.in.santuario.CompletarSesionBloqueoUseCase;
import com.renaser.os.habits.application.ports.in.santuario.IniciarSesionBloqueoUseCase;
import com.renaser.os.habits.application.ports.in.santuario.RomperSesionBloqueoUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.santuario.LoadSesionBloqueoPort;
import com.renaser.os.habits.application.ports.out.santuario.SaveSesionBloqueoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.registro.FaseOtorgamiento;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.registro.ResultadoOtorgamiento;
import com.renaser.os.habits.domain.model.registro.VentanaEntrega;
import com.renaser.os.habits.domain.model.santuario.SesionBloqueo;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Santuario (`SesionBloqueo`, habitos BLOQUEO) — traduccion 1:1 de `blocking.ts`
 * (repo viejo, paso 0 en docs/MODULO_HABITS.md).
 *
 * <p>Decision H-2 (documentada en docs/MODULO_HABITS.md): el repo viejo NO
 * otorga puntos explicitamente en {@code completeBlockSession} (solo penaliza
 * al romper). Se asumio, por consistencia con el resto del sistema de puntos,
 * que completar el Santuario SI otorga puntos con la misma escala que un
 * habito comun (ResultadoOtorgamiento) — es una decision menor, documentada,
 * no confirmada por negocio.
 */
@Service
public class SantuarioService implements IniciarSesionBloqueoUseCase, CompletarSesionBloqueoUseCase,
        RomperSesionBloqueoUseCase {

    private final LoadRegistroHabitoPort loadRegistroPort;
    private final SaveRegistroHabitoPort saveRegistroPort;
    private final LoadSesionBloqueoPort loadSesionPort;
    private final SaveSesionBloqueoPort saveSesionPort;
    private final LoadHabitoPort loadHabitoPort;
    private final LoadHorarioHabitoPort loadHorarioPort;
    private final LoadPreferenciaHorarioPort loadPreferenciaPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final AjustarPuntosPort ajustarPuntosPort;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SantuarioService(LoadRegistroHabitoPort loadRegistroPort, SaveRegistroHabitoPort saveRegistroPort,
                             LoadSesionBloqueoPort loadSesionPort, SaveSesionBloqueoPort saveSesionPort,
                             LoadHabitoPort loadHabitoPort, LoadHorarioHabitoPort loadHorarioPort,
                             LoadPreferenciaHorarioPort loadPreferenciaPort,
                             ConsultarProgresoParticipanteHabitsPort progresoPort, AjustarPuntosPort ajustarPuntosPort,
                             ApplicationEventPublisher events, Clock clock) {
        this.loadRegistroPort = loadRegistroPort;
        this.saveRegistroPort = saveRegistroPort;
        this.loadSesionPort = loadSesionPort;
        this.saveSesionPort = saveSesionPort;
        this.loadHabitoPort = loadHabitoPort;
        this.loadHorarioPort = loadHorarioPort;
        this.loadPreferenciaPort = loadPreferenciaPort;
        this.progresoPort = progresoPort;
        this.ajustarPuntosPort = ajustarPuntosPort;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SesionBloqueo iniciar(IniciarSesionBloqueoCommand command) {
        RegistroHabito registro = requireRegistro(command.registroId());
        requireSelf(command.actorId(), registro.participanteId());
        Habito habito = requireHabito(registro);
        if (!habito.esBloqueo()) {
            throw new IllegalArgumentException("Este habito no es de tipo Santuario (BLOQUEO)");
        }
        if (loadSesionPort.porRegistro(registro.id()).isPresent()) {
            throw new IllegalStateException("Ya existe una sesion de Santuario para este registro");
        }

        Instant ahora = clock.now();
        VentanaBloqueo ventana = resolverVentana(registro, habito);
        if (ventana != null && ahora.isBefore(ventana.disparo())) {
            throw new IllegalStateException("Todavia no es la hora de iniciar tu Santuario");
        }

        registro.iniciar(ahora);
        saveRegistroPort.save(registro);

        SesionBloqueo sesion = SesionBloqueo.iniciar(registro.id(), ahora);
        return saveSesionPort.save(sesion);
    }

    @Override
    @Transactional
    public SesionBloqueo completar(CompletarSesionBloqueoCommand command) {
        RegistroHabito registro = requireRegistro(command.registroId());
        requireSelf(command.actorId(), registro.participanteId());
        Habito habito = requireHabito(registro);
        SesionBloqueo sesion = requireSesion(registro.id());

        if (sesion.estaCompletada()) {
            return sesion; // idempotente (blocking.ts:160-162)
        }

        Instant ahora = clock.now();
        VentanaBloqueo ventana = resolverVentana(registro, habito);
        sesion.completar(ahora, ventana != null ? ventana.limite() : null);
        SesionBloqueo guardada = saveSesionPort.save(sesion);

        int puntos = 0;
        MotivoPuntos motivo = MotivoPuntos.HABIT_COMPLETED;
        if (ventana != null) {
            ResultadoOtorgamiento resultado = ResultadoOtorgamiento.calcular(ventana.limite(), ahora,
                    Duration.ofHours(habito.horasExtraEvidencia() != null ? habito.horasExtraEvidencia()
                            : VentanaEntrega.EXTENSION_DEFAULT_HORAS));
            puntos = resultado.puntos();
            motivo = resultado.fase() == FaseOtorgamiento.EXTENDIDO ? MotivoPuntos.HABIT_EXTENDED
                    : MotivoPuntos.HABIT_COMPLETED;
        }
        registro.completar(puntos, null, null, null, ahora);
        saveRegistroPort.save(registro);
        if (puntos > 0) {
            ajustarPuntosPort.ajustar(registro.participanteId(), motivo, puntos,
                    "Santuario completado: " + habito.titulo());
        }
        events.publishEvent(new HabitoCompletadoEvent(registro.id().value(), registro.participanteId(),
                habito.id().value(), puntos, ahora));
        return guardada;
    }

    @Override
    @Transactional
    public SesionBloqueo romper(RomperSesionBloqueoCommand command) {
        RegistroHabito registro = requireRegistro(command.registroId());
        requireSelf(command.actorId(), registro.participanteId());
        Habito habito = requireHabito(registro);
        SesionBloqueo sesion = requireSesion(registro.id());

        if (sesion.estaRota()) {
            return sesion; // idempotente (blocking.ts:209-211)
        }

        Instant ahora = clock.now();
        sesion.romper(command.motivo(), command.evidenciaBucket(), command.evidenciaRuta(), ahora);
        SesionBloqueo guardada = saveSesionPort.save(sesion);

        registro.marcarFallido(ahora);
        saveRegistroPort.save(registro);

        ajustarPuntosPort.ajustar(registro.participanteId(), MotivoPuntos.SANCTUARY_BREAK,
                -SesionBloqueo.PENALIZACION_ROTURA_PUNTOS, "Santuario roto: " + habito.titulo());
        events.publishEvent(new SantuarioRotoEvent(registro.id().value(), registro.participanteId(), ahora));
        return guardada;
    }

    private record VentanaBloqueo(Instant disparo, Instant limite) {
    }

    /** Mismo horario resuelto que VentanaEntrega, pero necesita AMBOS instantes (disparo y limite),
     * no solo el ancla — blocking.ts:22-50. Sin horario configurado (ninguno de los dos), null. */
    private VentanaBloqueo resolverVentana(RegistroHabito registro, Habito habito) {
        HorarioHabito vigente = loadHorarioPort.porHabito(habito.id()).stream()
                .filter(h -> h.aplicaEnDia(registro.diaPrograma(), registro.tipoDia()))
                .findFirst().orElse(null);

        LocalTime horaDisparo = vigente != null ? vigente.horaDisparo() : null;
        LocalTime horaLimite = vigente != null ? vigente.horaLimite() : null;

        Optional<PreferenciaHorario> pref = loadPreferenciaPort.porParticipanteYHabito(registro.participanteId(),
                habito.id());
        if (pref.isPresent()) {
            if (pref.get().horaDisparo() != null) {
                horaDisparo = pref.get().horaDisparo();
            }
            if (pref.get().horaLimite() != null) {
                horaLimite = pref.get().horaLimite();
            }
        }
        if (horaDisparo == null || horaLimite == null) {
            return null;
        }

        ZoneId zona = ZoneId.of(requireProgreso(registro.participanteId()).timezone());
        Instant inicioDia = registro.fechaEjecucion().atStartOfDay(zona).toInstant();
        Instant disparo = inicioDia.plus(Duration.ofMinutes(horaDisparo.getHour() * 60L + horaDisparo.getMinute()));
        Instant limite = inicioDia.plus(Duration.ofMinutes(horaLimite.getHour() * 60L + horaLimite.getMinute()));
        if (!limite.isAfter(disparo)) {
            limite = limite.plus(Duration.ofDays(1));
        }
        return new VentanaBloqueo(disparo, limite);
    }

    /**
     * Pertenencia Y estado de cuenta, en una sola guard clause. Antes el chequeo de
     * suspension vivia dentro de {@code resolverVentana}, que retorna temprano cuando el
     * habito no tiene horario ni preferencia — con los datos reales de hoy esa era la rama
     * comun, asi que un aprendiz SUSPENDIDO operaba igual. La autorizacion no puede
     * depender de si el habito tiene horario configurado.
     */
    private void requireSelf(UserId actorId, UserId participanteId) {
        if (!actorId.equals(participanteId)) {
            throw new NotAuthorizedException("Solo el propio participante puede operar su Santuario");
        }
        requireProgreso(participanteId);
    }

    /**
     * Carga CON BLOQUEO: todos sus llamadores mutan el registro y otorgan puntos. Sin el
     * lock, dos requests concurrentes leen ambas el mismo estado PENDIENTE, ambas pasan la
     * validacion del dominio y ambas pagan (verificado en vivo: 6 llamadas paralelas
     * devolvian 200 cada una, la 7a secuencial devolvia 409).
     */
    private RegistroHabito requireRegistro(RegistroHabitoId id) {
        return loadRegistroPort.byIdParaEscritura(id)
                .orElseThrow(() -> new NoSuchElementException("Registro no encontrado: " + id));
    }

    private Habito requireHabito(RegistroHabito registro) {
        return loadHabitoPort.byId(registro.habitoId())
                .orElseThrow(() -> new NoSuchElementException("Habito no encontrado: " + registro.habitoId()));
    }

    private SesionBloqueo requireSesion(RegistroHabitoId registroId) {
        return loadSesionPort.porRegistro(registroId)
                .orElseThrow(() -> new NoSuchElementException("No hay sesion de Santuario para este registro"));
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
