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
import com.renaser.os.habits.domain.model.horario.HorariosDelHabito;
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
import java.util.HashSet;
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
 *
 * <p><b>Lo que NO se avisa (spec 007).</b> Un habito que la persona apago para hoy —por fecha (V38) o por
 * dia de la semana (V40)— no recibe ni el aviso de inicio ni el de vencimiento aunque su registro del dia ya
 * exista (FR-011): es el mismo par de consultas con el que {@code RegistroService} decide no generar el
 * registro de un dia. Y un registro {@code EN_CURSO} (sesion especial abierta) no recibe el de inicio
 * (FR-015b): no tiene sentido anunciar que va a empezar lo que ya empezo.
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

    /**
     * Solo lo que todavia se puede hacer: un COMPLETADO/EXPIRADO/FALLIDO no tiene nada que avisar, y
     * tampoco un habito que la persona apago para ESTE dia (FR-011). Los apagados se leen una sola vez por
     * barrido y solo si queda algun registro vivo: quien ya completo todo no suma consultas.
     */
    private List<RegistroHabito> registrosVivosDe(UserId participanteId, LocalDate fechaEnSuZona) {
        List<RegistroHabito> sinTerminar = loadRegistroPort.porParticipanteYFecha(participanteId, fechaEnSuZona)
                .stream()
                .filter(registro -> !registro.estado().esTerminal())
                .toList();
        if (sinTerminar.isEmpty()) {
            return sinTerminar;
        }
        Set<HabitoId> apagados = apagadosEl(participanteId, fechaEnSuZona);
        return sinTerminar.stream().filter(registro -> !apagados.contains(registro.habitoId())).toList();
    }

    /**
     * Los habitos que el aprendiz apago para esa fecha (V38) o para ese dia de la semana (V40). La fecha
     * ya viene en SU zona, y el dia de la semana sale de ella: no se recalcula con la del servidor.
     */
    private Set<HabitoId> apagadosEl(UserId participanteId, LocalDate fechaEnSuZona) {
        Set<HabitoId> apagados = new HashSet<>(loadPreferenciaPort.habitosApagadosEn(participanteId, fechaEnSuZona));
        apagados.addAll(loadPreferenciaPort.habitosApagadosEnDiaSemana(participanteId, fechaEnSuZona.getDayOfWeek()));
        return apagados;
    }

    private int publicarAvisosDe(RegistroHabito registro, AgendaDelDia agenda, ZoneId zona) {
        Habito habito = agenda.habitos().get(registro.habitoId());
        if (habito == null) {
            return 0;
        }
        Instant ahora = clock.now();
        VentanaEntrega ventana = ventanaDe(registro, habito, agenda, zona);
        PreferenciaHorario preferencia = agenda.preferencias().get(habito.id());
        List<AvisoHabito> debidos = calculadoraPara(preferencia).debidosAhora(ventana, ahora).stream()
                .filter(aviso -> aviso.tipo().correspondeA(registro.estado()))
                .toList();
        PuntosEnJuego puntos = PuntosEnJuego.de(ventana, ahora);
        for (AvisoHabito aviso : debidos) {
            events.publishEvent(eventoDe(registro, aviso, puntos, new DatosDelHabito(habito, preferencia, zona,
                    agenda.conEsquemaPorDia().contains(habito.id()))));
        }
        return debidos.size();
    }

    /** D-184: el aviso de inicio con la antelacion del recordatorio que el aprendiz eligio. */
    private CalculadoraAvisosHabito calculadoraPara(PreferenciaHorario preferencia) {
        if (preferencia == null) {
            return calculadora;
        }
        return calculadora.conRecordatorioDelAprendiz(preferencia.recordatorioActivo(),
                preferencia.minutosRecordatorio());
    }

    /**
     * {@code null} cuando el habito no tiene ninguna hora configurada: no vence y no se avisa. D-200:
     * un registro generado por debajo del inicio de su horario avisa con la hora de ese horario.
     */
    private VentanaEntrega ventanaDe(RegistroHabito registro, Habito habito, AgendaDelDia agenda, ZoneId zona) {
        HorariosDelHabito horarios = HorariosDelHabito.de(agenda.horarios().getOrDefault(habito.id(), List.of()));
        int dia = horarios.diaEfectivoDeUnRegistro(registro.diaPrograma(), registro.tipoDia());
        HorarioHabito vigente = horarios.vigentesEn(dia, registro.tipoDia()).stream().findFirst().orElse(null);
        HorarioResuelto resuelto = HorarioResuelto.de(vigente, agenda.preferencias().get(habito.id()));
        if (resuelto.sinHorario()) {
            return null;
        }
        return VentanaEntrega.calcular(registro.fechaEjecucion(), resuelto.horaDisparo(), resuelto.horaLimite(),
                zona, habito.horasExtraEvidencia());
    }

    /**
     * El evento se publica SIEMPRE, aunque el aprendiz haya apagado el recordatorio de ese habito:
     * apagarlo quita el push (lo decide `notifications`, D-184), no el aviso en la bandeja ni el
     * del chat del acompanante, que tiene su propio interruptor.
     *
     * <p>Spec 007: la clave lleva el momento avisado (FR-013) y el evento viaja con ese momento y la zona
     * del aprendiz (FR-012), para que `notifications` escriba «te toca a las 07:00» —la hora del habito,
     * no minutos contados desde este barrido— y descarte el aviso que llegue cuando su momento ya paso.
     */
    private AvisoHabitoDebidoEvent eventoDe(RegistroHabito registro, AvisoHabito aviso, PuntosEnJuego puntos,
                                             DatosDelHabito datos) {
        PreferenciaHorario preferencia = datos.preferencia();
        return new AvisoHabitoDebidoEvent(registro.id().value(), registro.participanteId(), datos.habito().titulo(),
                aviso.tipo().name(), aviso.minutosQueFaltan(), puntos.siCompletaAhora(),
                aviso.tipo().claveIdempotencia(registro.id(), aviso.momento()),
                preferencia == null ? null : preferencia.recordatorioActivo(),
                preferencia == null ? null : preferencia.minutosRecordatorio(), clock.now(),
                datos.habito().id().value(), datos.habito().categoriaClave(), aviso.momento(), datos.zona().getId(),
                diaDelAviso(registro, datos.usaEsquemaPorDia()));
    }

    /**
     * Spec 008, R6: el dia de la semana (ISO) de la fecha del registro, que ya viene en la zona del aprendiz,
     * solo si el habito usa el esquema de alarmas por dia. Con el esquema diario va {@code null} y el push
     * conserva su etiqueta de siempre.
     */
    private static Integer diaDelAviso(RegistroHabito registro, boolean usaEsquemaPorDia) {
        return usaEsquemaPorDia ? registro.fechaEjecucion().getDayOfWeek().getValue() : null;
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
        return new AgendaDelDia(habitos, horarios, preferencias,
                loadPreferenciaPort.habitosConEsquemaPorDia(participanteId, habitoIds));
    }

    /** Lo que el evento necesita saber del habito de un registro, agrupado para no pasar seis parametros. */
    private record DatosDelHabito(Habito habito, PreferenciaHorario preferencia, ZoneId zona,
                                  boolean usaEsquemaPorDia) {
    }

    /**
     * El catalogo del dia ya resuelto en lote, para no repetir consultas por registro. {@code conEsquemaPorDia}
     * son los habitos con alguna fila de dia de la semana (spec 008): se piden UNA vez por barrido.
     */
    private record AgendaDelDia(Map<HabitoId, Habito> habitos, Map<HabitoId, List<HorarioHabito>> horarios,
                                 Map<HabitoId, PreferenciaHorario> preferencias, Set<HabitoId> conEsquemaPorDia) {
    }
}
