package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase.DiaDeLaSemana;
import com.renaser.os.habits.application.ports.in.preferencia.PlanDeLaSemanaUseCase;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CambioDeFecha;
import com.renaser.os.habits.domain.model.preferencia.FechaPropia;
import com.renaser.os.habits.domain.model.preferencia.PlanDeLaSemana;
import com.renaser.os.habits.domain.model.preferencia.RecordatorioDelDia;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;
import com.renaser.os.habits.domain.model.preferencia.SemanaDelHabito;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * «Solo esta semana» (spec 028, D-296): lee y escribe el plan de UN habito en las fechas de una semana.
 *
 * <p>El plan de siempre se lee con el mismo caso de uso que ya lo resuelve por dia de la semana ({@link EditarHorarioSemanalUseCase#consultar}):
 * la precedencia (cambio vigente, preferencia general, catalogo) vive ahi y no se copia. Encima va la fecha propia
 * ({@link PlanDeLaSemana}). Escribir une lo tocado con lo que la fecha ya tenia ({@link CambioDeFecha}) y guarda todo en una transaccion.
 */
@Service
public class PlanDeLaSemanaService implements PlanDeLaSemanaUseCase {

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadPreferenciaHorarioPort loadPreferenciaPort;
    private final SavePreferenciaHorarioPort savePreferenciaPort;
    private final EditarHorarioSemanalUseCase planDeSiempre;
    private final CatalogoAplicable catalogoAplicable;
    private final Clock clock;

    public PlanDeLaSemanaService(ConsultarProgresoParticipanteHabitsPort progresoPort,
                                 LoadPreferenciaHorarioPort loadPreferenciaPort, SavePreferenciaHorarioPort savePreferenciaPort,
                                 EditarHorarioSemanalUseCase planDeSiempre, CatalogoAplicable catalogoAplicable, Clock clock) {
        this.progresoPort = progresoPort;
        this.loadPreferenciaPort = loadPreferenciaPort;
        this.savePreferenciaPort = savePreferenciaPort;
        this.planDeSiempre = planDeSiempre;
        this.catalogoAplicable = catalogoAplicable;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanDeLaSemana.DiaDeLaFecha> consultar(UserId actorId, HabitoId habitoId, SemanaCalendaria semana) {
        ProgresoParticipanteHabits progreso = requireProgreso(actorId);
        Habito habito = requireAplicable(actorId, progreso, habitoId, semana);
        LocalDate hoy = hoyDe(progreso);
        return PlanDeLaSemana.resolver(semana, baseDe(actorId, habito), filasDe(actorId, habito, semana), hoy);
    }

    @Override
    @Transactional
    public void guardar(UserId actorId, HabitoId habitoId, SemanaCalendaria semana, List<CambioDeFecha> cambios) {
        ProgresoParticipanteHabits progreso = requireProgreso(actorId);
        Habito habito = requireAplicable(actorId, progreso, habitoId, semana);
        LocalDate hoy = hoyDe(progreso);
        requireCambiosValidos(semana, cambios, hoy);

        Map<DayOfWeek, SemanaDelHabito.Dia> base = baseDe(actorId, habito).stream()
                .collect(Collectors.toMap(SemanaDelHabito.Dia::diaSemana, Function.identity()));
        Map<LocalDate, FechaPropia> existentes = filasDe(actorId, habito, semana).stream()
                .collect(Collectors.toMap(FechaPropia::fecha, Function.identity(), (a, b) -> b));

        // Primero se calcula TODO (puede lanzar 400 o 409) y recien despues se escribe: todo o nada.
        List<FechaPropia> aGuardar = new java.util.ArrayList<>();
        Set<LocalDate> aBorrar = new HashSet<>();
        for (CambioDeFecha cambio : cambios) {
            var queda = cambio.aplicarA(habitoId, existentes.get(cambio.fecha()), base.get(cambio.fecha().getDayOfWeek()),
                    habito.desactivable());
            queda.ifPresentOrElse(aGuardar::add, () -> aBorrar.add(cambio.fecha()));
        }
        aBorrar.stream().filter(existentes::containsKey)
                .forEach(fecha -> savePreferenciaPort.borrarParaFecha(actorId, habitoId, fecha));
        savePreferenciaPort.guardarFechasPropias(actorId, aGuardar);
    }

    @Override
    @Transactional
    public void volverAlPlanDeSiempre(UserId actorId, HabitoId habitoId, SemanaCalendaria semana) {
        ProgresoParticipanteHabits progreso = requireProgreso(actorId);
        requireAplicable(actorId, progreso, habitoId, semana);
        LocalDate hoy = hoyDe(progreso);
        LocalDate desde = semana.lunes().isAfter(hoy) ? semana.lunes() : hoy.plusDays(1);
        if (!desde.isAfter(semana.domingo())) {
            savePreferenciaPort.borrarFechasPropias(actorId, List.of(habitoId), desde, semana.domingo());
        }
    }

    /** Cada fecha tiene que estar en la semana, ser posterior a hoy y no repetirse. */
    private static void requireCambiosValidos(SemanaCalendaria semana, List<CambioDeFecha> cambios, LocalDate hoy) {
        if (cambios == null || cambios.isEmpty()) {
            throw new IllegalArgumentException("No hay ningun cambio que guardar");
        }
        Set<LocalDate> vistas = new HashSet<>();
        for (CambioDeFecha cambio : cambios) {
            if (!semana.contiene(cambio.fecha())) {
                throw new IllegalArgumentException("La fecha " + cambio.fecha() + " no es de la semana que empieza el " + semana.lunes());
            }
            if (!cambio.fecha().isAfter(hoy)) {
                throw new IllegalArgumentException("Solo puedes cambiar dias que todavia no llegaron: " + cambio.fecha());
            }
            if (!vistas.add(cambio.fecha())) {
                throw new IllegalArgumentException("La fecha " + cambio.fecha() + " viene repetida");
            }
        }
    }

    /** Los siete dias de la semana del habito, tal como rigen en el plan de siempre. */
    private List<SemanaDelHabito.Dia> baseDe(UserId actorId, Habito habito) {
        return planDeSiempre.consultar(actorId, habito.id()).stream().map(PlanDeLaSemanaService::aDia).toList();
    }

    private static SemanaDelHabito.Dia aDia(DiaDeLaSemana d) {
        RecordatorioDelDia aviso = d.recordatorioActivo() && !d.antelaciones().isEmpty()
                ? RecordatorioDelDia.con(d.antelaciones()) : RecordatorioDelDia.sinAviso();
        return new SemanaDelHabito.Dia(d.diaSemana(), d.horaDisparo(), d.horaLimite(), d.propio(), d.activo(), aviso,
                d.recordatorioPropio(), d.alarma(), d.alarmaPropia());
    }

    private List<FechaPropia> filasDe(UserId actorId, Habito habito, SemanaCalendaria semana) {
        return loadPreferenciaPort.fechasPropiasDeUnHabitoEntre(actorId, habito.id(), semana.lunes(), semana.domingo());
    }

    /**
     * Solo se planifica un habito que hoy le corresponde a la persona (audiencia de rol + sus personales): si no, 404 sin escribir nada
     * (spec 028, FR-018). Un habito que existe pero no le corresponde no se distingue de uno que no existe.
     */
    private Habito requireAplicable(UserId actorId, ProgresoParticipanteHabits progreso, HabitoId habitoId, SemanaCalendaria semana) {
        return catalogoAplicable.aplicablesPara(actorId, progreso, semana.lunes()).stream()
                .filter(h -> h.id().equals(habitoId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Habito no encontrado: " + habitoId));
    }

    private LocalDate hoyDe(ProgresoParticipanteHabits progreso) {
        return clock.now().atZone(ZoneId.of(progreso.timezone())).toLocalDate();
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
