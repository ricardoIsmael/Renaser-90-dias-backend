package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.AvisoHabitoDebidoEvent;
import com.renaser.os.habits.application.ports.in.aviso.DespacharAvisosHabitoUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.domain.model.aviso.AvisoHabito;
import com.renaser.os.habits.domain.model.aviso.CalculadoraAvisosHabito;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioResuelto;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.registro.PuntosEnJuego;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.VentanaEntrega;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Los dos avisos automaticos de cada habito (pedido del dueno, 2026-09-05). La regla de CUANDO
 * avisar vive entera en {@link CalculadoraAvisosHabito}; este servicio solo junta los datos que
 * esa regla necesita y publica el evento.
 *
 * <p><b>El dia del aprendiz, no el del servidor</b> (regla 02 seccion 1, bug E-91). La fecha
 * contra la que se buscan los registros sale SIEMPRE de {@code clock.now().atZone(su zona)},
 * nunca de {@code clock.today()}. Con el padron en {@code America/Lima} (UTC-5), una corrida a
 * las 02:00 UTC que usara la fecha del servidor buscaria los habitos de MANANA y no encontraria
 * ninguno — justo en la franja nocturna en la que mas habitos estan por vencer.
 *
 * <p><b>Sin cola propia y sin migracion</b>: ver el javadoc de {@code TipoAvisoHabito}. El aviso
 * es derivable del calendario, y la deduplicacion la hace {@code notificaciones} por
 * {@code origen_evento_id} (C-7/V16).
 */
@Service
public class AvisoHabitoService implements DespacharAvisosHabitoUseCase {

    private final LoadRegistroHabitoPort loadRegistroPort;
    private final LoadHabitoPort loadHabitoPort;
    private final LoadHorarioHabitoPort loadHorarioPort;
    private final LoadPreferenciaHorarioPort loadPreferenciaPort;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final CalculadoraAvisosHabito calculadora;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AvisoHabitoService(LoadRegistroHabitoPort loadRegistroPort, LoadHabitoPort loadHabitoPort,
                               LoadHorarioHabitoPort loadHorarioPort, LoadPreferenciaHorarioPort loadPreferenciaPort,
                               ConsultarProgresoParticipanteHabitsPort progresoPort,
                               CalculadoraAvisosHabito calculadora, ApplicationEventPublisher events, Clock clock) {
        this.loadRegistroPort = loadRegistroPort;
        this.loadHabitoPort = loadHabitoPort;
        this.loadHorarioPort = loadHorarioPort;
        this.loadPreferenciaPort = loadPreferenciaPort;
        this.progresoPort = progresoPort;
        this.calculadora = calculadora;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int despacharDe(UserId participanteId) {
        Optional<ProgresoParticipanteHabits> progreso = progresoPort.deParticipante(participanteId);
        if (progreso.isEmpty() || progreso.get().suspendido()) {
            return 0;
        }
        ZoneId zona = ZoneId.of(progreso.get().timezone());
        Instant ahora = clock.now();
        List<RegistroHabito> vivos = registrosVivosDe(participanteId, ahora.atZone(zona).toLocalDate());
        if (vivos.isEmpty()) {
            return 0;
        }
        AgendaDelDia agenda = cargarAgenda(participanteId, vivos);
        int publicados = 0;
        for (RegistroHabito registro : vivos) {
            publicados += publicarAvisosDe(registro, agenda, zona);
        }
        return publicados;
    }

    /** Solo lo que todavia se puede hacer: un COMPLETADO/EXPIRADO/FALLIDO no tiene nada que avisar. */
    private List<RegistroHabito> registrosVivosDe(UserId participanteId, LocalDate fechaEnSuZona) {
        return loadRegistroPort.porParticipanteYFecha(participanteId, fechaEnSuZona).stream()
                .filter(registro -> !registro.estado().esTerminal())
                .toList();
    }

    private int publicarAvisosDe(RegistroHabito registro, AgendaDelDia agenda, ZoneId zona) {
        Habito habito = agenda.habitos().get(registro.habitoId());
        if (habito == null) {
            return 0;
        }
        Instant ahora = clock.now();
        VentanaEntrega ventana = ventanaDe(registro, habito, agenda, zona);
        List<AvisoHabito> debidos = calculadora.debidosAhora(ventana, ahora);
        PuntosEnJuego puntos = PuntosEnJuego.de(ventana, ahora);
        for (AvisoHabito aviso : debidos) {
            events.publishEvent(eventoDe(registro, habito, aviso, puntos));
        }
        return debidos.size();
    }

    /** {@code null} cuando el habito no tiene ninguna hora configurada: no vence y no se avisa. */
    private VentanaEntrega ventanaDe(RegistroHabito registro, Habito habito, AgendaDelDia agenda, ZoneId zona) {
        HorarioHabito vigente = agenda.horarios().getOrDefault(habito.id(), List.of()).stream()
                .filter(horario -> horario.aplicaEnDia(registro.diaPrograma(), registro.tipoDia()))
                .findFirst().orElse(null);
        HorarioResuelto resuelto = HorarioResuelto.de(vigente, agenda.preferencias().get(habito.id()));
        if (resuelto.sinHorario()) {
            return null;
        }
        return VentanaEntrega.calcular(registro.fechaEjecucion(), resuelto.horaDisparo(), resuelto.horaLimite(),
                zona, habito.horasExtraEvidencia());
    }

    private AvisoHabitoDebidoEvent eventoDe(RegistroHabito registro, Habito habito, AvisoHabito aviso,
                                             PuntosEnJuego puntos) {
        return new AvisoHabitoDebidoEvent(registro.id().value(), registro.participanteId(), habito.titulo(),
                aviso.tipo().name(), aviso.minutosQueFaltan(), puntos.siCompletaAhora(),
                aviso.tipo().claveIdempotencia(registro.id()), clock.now());
    }

    /** Una consulta por coleccion y no una por registro: este barrido recorre todo el padron. */
    private AgendaDelDia cargarAgenda(UserId participanteId, List<RegistroHabito> registros) {
        Set<HabitoId> habitoIds = registros.stream().map(RegistroHabito::habitoId).collect(Collectors.toSet());
        Map<HabitoId, Habito> habitos = loadHabitoPort.porIds(habitoIds).stream()
                .collect(Collectors.toMap(Habito::id, habito -> habito));
        Map<HabitoId, List<HorarioHabito>> horarios = new HashMap<>();
        for (HorarioHabito horario : loadHorarioPort.porHabitos(habitoIds)) {
            horarios.computeIfAbsent(horario.habitoId(), clave -> new ArrayList<>()).add(horario);
        }
        Map<HabitoId, PreferenciaHorario> preferencias = loadPreferenciaPort
                .porParticipanteHabitosYFecha(participanteId, habitoIds, registros.getFirst().fechaEjecucion()).stream()
                .collect(Collectors.toMap(PreferenciaHorario::habitoId, preferencia -> preferencia));
        return new AgendaDelDia(habitos, horarios, preferencias);
    }

    /** El catalogo del dia ya resuelto en lote, para no repetir consultas por registro. */
    private record AgendaDelDia(Map<HabitoId, Habito> habitos, Map<HabitoId, List<HorarioHabito>> horarios,
                                 Map<HabitoId, PreferenciaHorario> preferencias) {
    }
}
