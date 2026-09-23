package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.HorarioDelDiaFinder;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.CambioProgramado;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.CuotaEdicion;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.HorarioDeHabito;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.ResumenPreferenciasHorario;
import com.renaser.os.habits.application.ports.out.desbloqueo.LoadDesbloqueoHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CuotaEdicionHorario;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementa {@link HorarioDelDiaFinder} (2026-09-23). Fachada delgada, mismo patron que
 * {@link AgendaDelDiaFinderService}: el horario resuelto y la cuota los da
 * {@link ConsultarPreferenciasHorarioUseCase} — el mismo que responde
 * {@code GET /api/v1/habit-preferences?date=} —, y aca NO se reimplementa ni la precedencia ni el
 * conteo de cupo.
 *
 * <p>Lo unico que suma son tres marcas que aquel GET no trae y que el acompanante necesita para no
 * proponer algo imposible, cada una leida con la regla que ya existe:
 * <ul>
 *   <li><b>apagado</b>: los mismos dos puertos que junta {@code RegistroService.generarInterno}
 *       (apagado por fecha, V38, y por dia de semana, V40);</li>
 *   <li><b>pausado</b>: {@link DesbloqueoHabito#estaPausadoEl}, igual que el generador;</li>
 *   <li><b>obligatorio</b>: {@code Habito.desactivable()} (V18).</li>
 * </ul>
 *
 * <p><b>"Hoy" se resuelve aca, en la zona del participante</b> (regla 02, E-91), y se le pasa al
 * caso de uso ya como fecha explicita: asi las marcas y el horario hablan del MISMO dia.
 */
@Service
public class HorarioDelDiaFinderService implements HorarioDelDiaFinder {

    private final ConsultarPreferenciasHorarioUseCase consultarPreferenciasUseCase;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadHabitoPort loadHabitoPort;
    private final LoadPreferenciaHorarioPort loadPreferenciaPort;
    private final LoadDesbloqueoHabitoPort loadDesbloqueoPort;
    private final Clock clock;

    public HorarioDelDiaFinderService(ConsultarPreferenciasHorarioUseCase consultarPreferenciasUseCase,
                                      ConsultarProgresoParticipanteHabitsPort progresoPort,
                                      LoadHabitoPort loadHabitoPort, LoadPreferenciaHorarioPort loadPreferenciaPort,
                                      LoadDesbloqueoHabitoPort loadDesbloqueoPort, Clock clock) {
        this.consultarPreferenciasUseCase = consultarPreferenciasUseCase;
        this.progresoPort = progresoPort;
        this.loadHabitoPort = loadHabitoPort;
        this.loadPreferenciaPort = loadPreferenciaPort;
        this.loadDesbloqueoPort = loadDesbloqueoPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public HorariosDelDia deFecha(UserId participanteId, LocalDate fecha) {
        ProgresoParticipanteHabits progreso = progresoPort.deParticipante(participanteId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + participanteId));
        ZoneId zona = ZoneId.of(progreso.timezone());
        LocalDate hoy = clock.now().atZone(zona).toLocalDate();
        DiaConsultado dia = new DiaConsultado(participanteId, fecha == null ? hoy : fecha, zona);
        // El caso de uso valida suspension y resuelve horario + cuota: no se repite nada de eso.
        ResumenPreferenciasHorario resumen = consultarPreferenciasUseCase.consultar(participanteId, dia.fecha());
        // Misma cuenta que usa el caso de uso para su `diaObjetivo`, que no la expone.
        int diaPrograma = Math.toIntExact(progreso.diaPrograma() + ChronoUnit.DAYS.between(hoy, dia.fecha()));
        return new HorariosDelDia(dia.fecha(), diaPrograma, horariosDe(dia, resumen.habitos()),
                cuotaDe(resumen.cuota()));
    }

    private List<HorarioResuelto> horariosDe(DiaConsultado dia, List<HorarioDeHabito> horarios) {
        if (horarios.isEmpty()) {
            return List.of();
        }
        Set<HabitoId> apagados = apagadosEn(dia);
        Set<HabitoId> pausados = pausadosEn(dia);
        Set<HabitoId> obligatorios = obligatoriosEntre(horarios);
        return horarios.stream()
                .map(horario -> new HorarioResuelto(horario.habitoId().value(), horario.titulo(),
                        horario.horaDisparo(), horario.horaLimite(), horario.personalizado(),
                        apagados.contains(horario.habitoId()), pausados.contains(horario.habitoId()),
                        obligatorios.contains(horario.habitoId()), cambioDe(horario.cambioProgramado())))
                .toList();
    }

    /** Los dos interruptores que junta el generador diario: por fecha (V38) y por dia de semana (V40). */
    private Set<HabitoId> apagadosEn(DiaConsultado dia) {
        Set<HabitoId> apagados = new HashSet<>(loadPreferenciaPort.habitosApagadosEn(dia.participanteId(),
                dia.fecha()));
        apagados.addAll(loadPreferenciaPort.habitosApagadosEnDiaSemana(dia.participanteId(),
                dia.fecha().getDayOfWeek()));
        return apagados;
    }

    private Set<HabitoId> pausadosEn(DiaConsultado dia) {
        return loadDesbloqueoPort.deParticipante(dia.participanteId()).stream()
                .filter(desbloqueo -> desbloqueo.estaPausadoEl(dia.fecha(), dia.zona()))
                .map(DesbloqueoHabito::habitoId)
                .collect(Collectors.toSet());
    }

    /** UNA consulta para todos los habitos del dia, nunca una por habito. */
    private Set<HabitoId> obligatoriosEntre(List<HorarioDeHabito> horarios) {
        Set<HabitoId> ids = horarios.stream().map(HorarioDeHabito::habitoId).collect(Collectors.toSet());
        return loadHabitoPort.porIds(ids).stream()
                .filter(habito -> !habito.desactivable())
                .map(Habito::id)
                .collect(Collectors.toSet());
    }

    private static CambioHorarioProgramado cambioDe(CambioProgramado cambio) {
        return cambio == null ? null
                : new CambioHorarioProgramado(cambio.horaDisparo(), cambio.horaLimite(), cambio.fechaEfectiva());
    }

    private static CuotaCambiosHorario cuotaDe(CuotaEdicion cuota) {
        return new CuotaCambiosHorario(cuota.cambiosUsados(), cuota.cambiosRestantes(), cuota.cambiosLimite(),
                CuotaEdicionHorario.PERIODO_LIBRE.equals(cuota.periodo()));
    }

    /** El dia que se consulta, ya en el calendario del participante, con lo necesario para leerlo. */
    private record DiaConsultado(UserId participanteId, LocalDate fecha, ZoneId zona) {
    }
}
