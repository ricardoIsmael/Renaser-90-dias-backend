package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.HabitoCompletadoEvent;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.in.registro.ExpirarRegistrosVencidosUseCase;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.FaseOtorgamiento;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.registro.ResultadoOtorgamiento;
import com.renaser.os.habits.domain.model.registro.VentanaEntrega;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.points.api.MotivoPuntos;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Servicio del agregado `registro/` — el corazon del modulo. Integra
 * sincronicamente con `points.api.AjustarPuntosPort` DENTRO de la misma
 * transaccion que completa el registro (CLAUDE.MD §9.1: la misma garantia
 * atomica que ya usa `points`, no un evento).
 */
@Service
public class RegistroService implements ConsultarTracksDelDiaUseCase, GenerarTracksDelDiaUseCase,
        CompletarRegistroUseCase, ExpirarRegistrosVencidosUseCase {

    private final LoadRegistroHabitoPort loadRegistroPort;
    private final SaveRegistroHabitoPort saveRegistroPort;
    private final LoadHabitoPort loadHabitoPort;
    private final LoadHorarioHabitoPort loadHorarioPort;
    private final LoadPreferenciaHorarioPort loadPreferenciaPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final AjustarPuntosPort ajustarPuntosPort;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RegistroService(LoadRegistroHabitoPort loadRegistroPort, SaveRegistroHabitoPort saveRegistroPort,
                            LoadHabitoPort loadHabitoPort, LoadHorarioHabitoPort loadHorarioPort,
                            LoadPreferenciaHorarioPort loadPreferenciaPort,
                            ConsultarProgresoParticipanteHabitsPort progresoPort, AjustarPuntosPort ajustarPuntosPort,
                            ApplicationEventPublisher events, Clock clock) {
        this.loadRegistroPort = loadRegistroPort;
        this.saveRegistroPort = saveRegistroPort;
        this.loadHabitoPort = loadHabitoPort;
        this.loadHorarioPort = loadHorarioPort;
        this.loadPreferenciaPort = loadPreferenciaPort;
        this.progresoPort = progresoPort;
        this.ajustarPuntosPort = ajustarPuntosPort;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public List<RegistroHabito> consultar(UserId actorId, UserId participanteId, LocalDate fecha) {
        requireSelf(actorId, participanteId);
        return loadRegistroPort.porParticipanteYFecha(participanteId, fecha);
    }

    @Override
    @Transactional
    public List<RegistroHabito> generar(UserId participanteId, LocalDate fecha) {
        ProgresoParticipanteHabits progreso = requireProgreso(participanteId);
        TipoDia tipoDia = resolverTipoDia(fecha);
        Instant ahora = clock.now();

        List<Habito> catalogo = new ArrayList<>(loadHabitoPort.catalogoActivo());
        catalogo.addAll(loadHabitoPort.personalesActivosDe(participanteId));

        List<RegistroHabito> generados = new ArrayList<>();
        for (Habito habito : catalogo) {
            if (loadRegistroPort.porParticipanteHabitoYFecha(participanteId, habito.id(), fecha).isPresent()) {
                continue; // idempotente: ya existe (UNIQUE participante+habito+fecha)
            }
            boolean aplicaHoy = loadHorarioPort.porHabito(habito.id()).stream()
                    .anyMatch(h -> h.aplicaEnDia(progreso.diaPrograma(), tipoDia));
            if (!aplicaHoy) {
                continue;
            }
            RegistroHabito registro = RegistroHabito.generar(participanteId, habito.id(), fecha,
                    progreso.diaPrograma(), tipoDia, habito.esOpcional(), ahora);
            generados.add(saveRegistroPort.save(registro));
        }
        return generados;
    }

    @Override
    @Transactional
    public RegistroHabito completar(CompletarRegistroCommand command) {
        RegistroHabito registro = requireRegistro(command.registroId());
        requireSelf(command.actorId(), registro.participanteId());
        Habito habito = requireHabito(registro.habitoId());
        if (habito.esBloqueo()) {
            throw new IllegalArgumentException(
                    "Los habitos BLOQUEO (Santuario) se completan via /habit-tracks/{id}/santuario, no aca");
        }

        Instant ahora = clock.now();
        VentanaEntrega ventana = resolverVentana(registro, habito);
        if (ventana != null && ventana.vencida(ahora)) {
            registro.expirar(ahora);
            saveRegistroPort.save(registro);
            throw new IllegalStateException("El habito expiro — no se puede completar");
        }

        int puntos = 0;
        MotivoPuntos motivo = MotivoPuntos.HABIT_COMPLETED;
        if (ventana != null) {
            ResultadoOtorgamiento resultado = ResultadoOtorgamiento.calcular(ventana.instanteAncla(), ahora,
                    ventana.extension());
            puntos = resultado.puntos();
            motivo = resultado.fase() == FaseOtorgamiento.EXTENDIDO ? MotivoPuntos.HABIT_EXTENDED
                    : MotivoPuntos.HABIT_COMPLETED;
        }

        registro.completar(puntos, command.respuestaTexto(), command.calificacionProductividad(), null, ahora);
        RegistroHabito guardado = saveRegistroPort.save(registro);

        if (puntos > 0) {
            ajustarPuntosPort.ajustar(registro.participanteId(), motivo, puntos,
                    "Habito completado: " + habito.titulo());
        }
        events.publishEvent(new HabitoCompletadoEvent(guardado.id().value(), guardado.participanteId(),
                habito.id().value(), puntos, ahora));
        return guardado;
    }

    @Override
    @Transactional
    public int expirarPendientesAnterioresA(LocalDate hoy) {
        List<RegistroHabito> vencidos = loadRegistroPort.enEstadoConFechaAnteriorA(EstadoRegistro.PENDIENTE, hoy);
        Instant ahora = clock.now();
        for (RegistroHabito registro : vencidos) {
            registro.expirar(ahora);
            saveRegistroPort.save(registro);
        }
        return vencidos.size();
    }

    /** DOMINGO por dia de calendario; DISCIPLINA en cualquier otro caso. INTOXICACION (ciclos fijos del
     * repo viejo) NO esta implementado en esta version — ver docs/MODULO_HABITS.md "que quedo simplificado". */
    private TipoDia resolverTipoDia(LocalDate fecha) {
        return fecha.getDayOfWeek() == DayOfWeek.SUNDAY ? TipoDia.DOMINGO : TipoDia.DISCIPLINA;
    }

    /** preferencia -&gt; horario del catalogo vigente para el dia de programa del registro. */
    private VentanaEntrega resolverVentana(RegistroHabito registro, Habito habito) {
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
        if (horaDisparo == null && horaLimite == null) {
            return null;
        }

        ProgresoParticipanteHabits progreso = requireProgreso(registro.participanteId());
        ZoneId zona = ZoneId.of(progreso.timezone());
        return VentanaEntrega.calcular(registro.fechaEjecucion(), horaDisparo, horaLimite, zona,
                habito.horasExtraEvidencia());
    }

    /**
     * Pertenencia Y estado de cuenta, en una sola guard clause al principio de cada caso de
     * uso. Antes el chequeo de suspension vivia dentro de {@code resolverVentana}, que
     * retorna temprano cuando el habito no tiene horario ni preferencia — con los datos
     * reales de hoy (ningun habito tiene fila en `horarios_habito`) esa rama era la comun,
     * asi que un aprendiz SUSPENDIDO podia operar igual. La autorizacion no puede depender
     * de si el habito tiene horario configurado.
     */
    private void requireSelf(UserId actorId, UserId participanteId) {
        if (!actorId.equals(participanteId)) {
            throw new NotAuthorizedException("Solo el propio participante puede operar sobre sus habitos");
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

    private Habito requireHabito(HabitoId id) {
        return loadHabitoPort.byId(id).orElseThrow(() -> new NoSuchElementException("Habito no encontrado: " + id));
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
