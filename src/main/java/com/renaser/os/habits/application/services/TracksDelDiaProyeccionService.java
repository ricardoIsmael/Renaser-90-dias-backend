package com.renaser.os.habits.application.services;

import com.renaser.os.evidence.api.RegistrosConEvidenciaFinder;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.guia.LoadGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.guia.GuiaHabito;
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
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Hueco #10 — proyeccion de lectura para {@code GET /habit-tracks/today}: por cada
 * registro del dia, el catalogo resuelto (titulo, tipo, guia, horario). Delega la
 * autorizacion (requireSelf/progreso) en {@link ConsultarTracksDelDiaUseCase}, ya
 * probada — este servicio solo agrega el batch de catalogo encima, sin repetir esa
 * logica.
 *
 * <p><b>Nunca N+1:</b> habitos/horarios/preferencias/guias se piden UNA vez cada uno,
 * por el conjunto de {@code habitoId} de los registros del dia (tipicamente 20-40),
 * no una consulta por registro. Desde D-113 son cinco consultas de lote y no cuatro: la
 * quinta le pregunta a {@code evidence} cuales de esos registros ya tienen evidencia
 * ({@code RegistrosConEvidenciaFinder}), para que el cliente no tenga que reconstruirlo
 * cruzando dos endpoints paginados.
 */
@Service
public class TracksDelDiaProyeccionService implements ConsultarTracksDelDiaConCatalogoUseCase {

    private final ConsultarTracksDelDiaUseCase consultarTracksUseCase;
    private final GenerarTracksDelDiaUseCase generarTracksUseCase;
    private final LoadHabitoPort loadHabitoPort;
    private final LoadHorarioHabitoPort loadHorarioPort;
    private final LoadPreferenciaHorarioPort loadPreferenciaPort;
    private final LoadGuiaHabitoPort loadGuiaPort;
    /** Para resolver la ventana de entrega en la zona del participante y con ella los puntos en juego. */
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    /** API publica de {@code evidence} (D-41): {@code habits} nunca consulta {@code evidencias} de frente. */
    private final RegistrosConEvidenciaFinder registrosConEvidenciaFinder;
    private final Clock clock;

    public TracksDelDiaProyeccionService(ConsultarTracksDelDiaUseCase consultarTracksUseCase,
                                          GenerarTracksDelDiaUseCase generarTracksUseCase,
                                          LoadHabitoPort loadHabitoPort, LoadHorarioHabitoPort loadHorarioPort,
                                          LoadPreferenciaHorarioPort loadPreferenciaPort,
                                          LoadGuiaHabitoPort loadGuiaPort,
                                          ConsultarProgresoParticipanteHabitsPort progresoPort,
                                          RegistrosConEvidenciaFinder registrosConEvidenciaFinder, Clock clock) {
        this.consultarTracksUseCase = consultarTracksUseCase;
        this.generarTracksUseCase = generarTracksUseCase;
        this.loadHabitoPort = loadHabitoPort;
        this.loadHorarioPort = loadHorarioPort;
        this.loadPreferenciaPort = loadPreferenciaPort;
        this.loadGuiaPort = loadGuiaPort;
        this.progresoPort = progresoPort;
        this.registrosConEvidenciaFinder = registrosConEvidenciaFinder;
        this.clock = clock;
    }

    /**
     * "Hoy" en la zona del participante, nunca la del servidor (E-105, misma familia que E-91).
     * Se resuelve aca y no en el controller porque es una decision de dominio: el dia de una
     * persona empieza donde esa persona esta.
     */
    @Override
    public List<TrackDelDiaConCatalogo> consultarHoyDe(UserId participanteId) {
        LocalDate hoyEnSuZona = momentoDe(participanteId).hoy();
        return consultar(participanteId, participanteId, hoyEnSuZona);
    }

    @Override
    public List<TrackDelDiaConCatalogo> consultar(UserId actorId, UserId participanteId, LocalDate fecha) {
        List<RegistroHabito> registros = consultarTracksUseCase.consultar(actorId, participanteId, fecha);
        if (registros.isEmpty() && actorId.equals(participanteId)) {
            // Red de seguridad: el barrido nocturno es la via normal, pero alguien que activa
            // su programa hoy mismo -o a quien la corrida de anoche no alcanzo- no tendria
            // NINGUN habito hasta manana. Se generan solo los que todavia puede completar a
            // esta hora (ver GenerarTracksDelDiaUseCase.generarDisponiblesAhora).
            // Solo para el propio actor: un mentor mirando los habitos de su aprendiz no debe
            // provocarle escrituras.
            generarTracksUseCase.generarDisponiblesAhora(participanteId);
            registros = consultarTracksUseCase.consultar(actorId, participanteId, fecha);
        }
        if (registros.isEmpty()) {
            return List.of();
        }
        Set<HabitoId> habitoIds = registros.stream().map(RegistroHabito::habitoId).collect(Collectors.toSet());

        Map<HabitoId, Habito> habitosPorId = loadHabitoPort.porIds(habitoIds).stream()
                .collect(Collectors.toMap(Habito::id, h -> h));
        Map<HabitoId, List<HorarioHabito>> horariosPorHabito = agruparPorHabito(loadHorarioPort.porHabitos(habitoIds),
                HorarioHabito::habitoId);
        Map<HabitoId, List<GuiaHabito>> guiasPorHabito = agruparPorHabito(loadGuiaPort.porHabitos(habitoIds),
                GuiaHabito::habitoId);
        Map<HabitoId, PreferenciaHorario> preferenciasPorHabito = loadPreferenciaPort
                .porParticipanteYHabitos(participanteId, habitoIds).stream()
                .collect(Collectors.toMap(PreferenciaHorario::habitoId, p -> p));

        // Una sola consulta por TODO el dia, igual que las cuatro de arriba — nunca una por registro.
        Set<UUID> conEvidencia = registrosConEvidenciaFinder.deEntre(
                registros.stream().map(r -> r.id().value()).toList());

        MomentoDelParticipante momento = momentoDe(participanteId);
        return registros.stream()
                .map(registro -> construirVista(registro, new CatalogoDeHabito(
                        habitosPorId.get(registro.habitoId()),
                        horariosPorHabito.getOrDefault(registro.habitoId(), List.of()),
                        guiasPorHabito.getOrDefault(registro.habitoId(), List.of()),
                        preferenciasPorHabito.get(registro.habitoId())), momento,
                        conEvidencia.contains(registro.id().value())))
                .toList();
    }

    /**
     * La zona del PARTICIPANTE, no la del servidor (regla 02, bug E-91): con el padron en
     * America/Lima, calcular la ventana de entrega en UTC corre el plazo cinco horas y con el
     * los puntos en juego. Si el participante no tiene progreso (no deberia llegar aca, porque
     * {@code consultarTracksUseCase} ya lo exige), se cae a UTC y los puntos quedan como si el
     * habito no tuviera horario — nunca se rompe la lectura de la pantalla por esto.
     */
    private MomentoDelParticipante momentoDe(UserId participanteId) {
        ZoneId zona = progresoPort.deParticipante(participanteId)
                .map(progreso -> ZoneId.of(progreso.timezone()))
                .orElse(ZoneId.of("UTC"));
        return new MomentoDelParticipante(zona, clock.now());
    }

    private static TrackDelDiaConCatalogo construirVista(RegistroHabito registro, CatalogoDeHabito catalogo,
                                                          MomentoDelParticipante momento, boolean tieneEvidencia) {
        Habito habito = catalogo.habito();
        String titulo = habito != null ? habito.titulo() : null;
        var tipo = habito != null ? habito.tipo() : null;
        GuiaResumen guia = resolverGuia(catalogo.guias(), registro.diaPrograma());
        HorarioHabito horarioVigente = catalogo.horarios().stream()
                .filter(h -> h.aplicaEnDia(registro.diaPrograma(), registro.tipoDia())).findFirst().orElse(null);
        HorarioResuelto horario = HorarioResuelto.de(horarioVigente, catalogo.preferencia());
        return new TrackDelDiaConCatalogo(registro, titulo, tipo, guia, horario.horaDisparo(), horario.horaLimite(),
                puntosEnJuegoDe(registro, catalogo, horario, momento), tieneEvidencia);
    }

    /**
     * {@code null} en los estados terminales: un habito ya completado, vencido o fallido no
     * tiene nada en juego, y devolver "10 puntos" ahi seria mentirle a la pantalla.
     */
    private static PuntosEnJuego puntosEnJuegoDe(RegistroHabito registro, CatalogoDeHabito catalogo,
                                                   HorarioResuelto horario, MomentoDelParticipante momento) {
        if (registro.estado().esTerminal()) {
            return null;
        }
        VentanaEntrega ventana = horario.sinHorario() ? null
                : VentanaEntrega.calcular(registro.fechaEjecucion(), horario.horaDisparo(), horario.horaLimite(),
                        momento.zona(), catalogo.habito() != null ? catalogo.habito().horasExtraEvidencia() : null);
        return PuntosEnJuego.de(ventana, momento.ahora());
    }

    /** La guia vigente es la de mayor {@code diaInicio} que todavia aplica — la mas especifica/reciente. */
    private static GuiaResumen resolverGuia(List<GuiaHabito> guias, int diaPrograma) {
        return guias.stream().filter(g -> g.aplicaEnDia(diaPrograma))
                .max(Comparator.comparingInt(GuiaHabito::diaInicio))
                .map(g -> new GuiaResumen(g.mantraTitulo(), g.mantraIntro(), g.queHacer(), g.comoHacerlo()))
                .orElse(null);
    }

    private static <T> Map<HabitoId, List<T>> agruparPorHabito(List<T> items,
                                                                 java.util.function.Function<T, HabitoId> claveDe) {
        Map<HabitoId, List<T>> agrupado = new HashMap<>();
        for (T item : items) {
            agrupado.computeIfAbsent(claveDe.apply(item), k -> new java.util.ArrayList<>()).add(item);
        }
        return agrupado;
    }

    /** Todo lo del catalogo que le toca a UN registro, ya resuelto del batch. Existe para que
     * {@code construirVista} no tenga que recibir cinco parametros sueltos. */
    private record CatalogoDeHabito(Habito habito, List<HorarioHabito> horarios, List<GuiaHabito> guias,
                                     PreferenciaHorario preferencia) {
    }

    /** Contra que instante y en que zona se mide la ventana de entrega de este participante. */
    private record MomentoDelParticipante(ZoneId zona, Instant ahora) {

        /** La fecha de HOY para esta persona — no la del servidor (E-91, E-105). */
        LocalDate hoy() {
            return ahora.atZone(zona).toLocalDate();
        }
    }
}
