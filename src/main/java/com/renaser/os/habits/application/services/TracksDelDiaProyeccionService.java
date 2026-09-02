package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.guia.LoadGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.guia.GuiaHabito;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * no una consulta por registro.
 */
@Service
public class TracksDelDiaProyeccionService implements ConsultarTracksDelDiaConCatalogoUseCase {

    private final ConsultarTracksDelDiaUseCase consultarTracksUseCase;
    private final GenerarTracksDelDiaUseCase generarTracksUseCase;
    private final LoadHabitoPort loadHabitoPort;
    private final LoadHorarioHabitoPort loadHorarioPort;
    private final LoadPreferenciaHorarioPort loadPreferenciaPort;
    private final LoadGuiaHabitoPort loadGuiaPort;

    public TracksDelDiaProyeccionService(ConsultarTracksDelDiaUseCase consultarTracksUseCase,
                                          GenerarTracksDelDiaUseCase generarTracksUseCase,
                                          LoadHabitoPort loadHabitoPort, LoadHorarioHabitoPort loadHorarioPort,
                                          LoadPreferenciaHorarioPort loadPreferenciaPort,
                                          LoadGuiaHabitoPort loadGuiaPort) {
        this.consultarTracksUseCase = consultarTracksUseCase;
        this.generarTracksUseCase = generarTracksUseCase;
        this.loadHabitoPort = loadHabitoPort;
        this.loadHorarioPort = loadHorarioPort;
        this.loadPreferenciaPort = loadPreferenciaPort;
        this.loadGuiaPort = loadGuiaPort;
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

        return registros.stream()
                .map(registro -> construirVista(registro, habitosPorId.get(registro.habitoId()),
                        horariosPorHabito.getOrDefault(registro.habitoId(), List.of()),
                        guiasPorHabito.getOrDefault(registro.habitoId(), List.of()),
                        preferenciasPorHabito.get(registro.habitoId())))
                .toList();
    }

    private static TrackDelDiaConCatalogo construirVista(RegistroHabito registro, Habito habito,
                                                           List<HorarioHabito> horarios, List<GuiaHabito> guias,
                                                           PreferenciaHorario preferencia) {
        String titulo = habito != null ? habito.titulo() : null;
        var tipo = habito != null ? habito.tipo() : null;
        GuiaResumen guia = resolverGuia(guias, registro.diaPrograma());
        HorarioHabito horarioVigente = horarios.stream()
                .filter(h -> h.aplicaEnDia(registro.diaPrograma(), registro.tipoDia())).findFirst().orElse(null);
        LocalTime horaDisparo = resolverHora(preferencia != null ? preferencia.horaDisparo() : null,
                horarioVigente != null ? horarioVigente.horaDisparo() : null);
        LocalTime horaLimite = resolverHora(preferencia != null ? preferencia.horaLimite() : null,
                horarioVigente != null ? horarioVigente.horaLimite() : null);
        return new TrackDelDiaConCatalogo(registro, titulo, tipo, guia, horaDisparo, horaLimite);
    }

    /** Preferencia del participante gana si esta seteada; si no, el default del catalogo. */
    private static LocalTime resolverHora(LocalTime dePreferencia, LocalTime deCatalogo) {
        return dePreferencia != null ? dePreferencia : deCatalogo;
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
}
