package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CombinacionDeAlarma;
import com.renaser.os.habits.domain.model.preferencia.FechaPropia;
import com.renaser.os.habits.domain.model.preferencia.RepeticionDeSemana;
import com.renaser.os.habits.domain.model.preferencia.RepeticionDeSemana.CambioPlaneado;
import com.renaser.os.habits.domain.model.preferencia.RepeticionDeSemana.PlanDeRepeticion;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Repetir la semana (spec 028, D-296). Orquesta lo que {@link RepeticionDeSemana} decide: carga las filas de las dos semanas, los habitos que
 * hoy le corresponden a la persona y, al aplicar, escribe y borra en UNA transaccion. No contiene reglas: la regla de que se copia y que
 * se reemplaza es del dominio.
 */
@Service
public class RepeticionDeSemanaService implements RepetirSemanaUseCase {

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadHabitoPort loadHabitoPort;
    private final LoadPreferenciaHorarioPort loadPreferenciaPort;
    private final SavePreferenciaHorarioPort savePreferenciaPort;
    private final CatalogoAplicable catalogoAplicable;
    private final Clock clock;

    public RepeticionDeSemanaService(ConsultarProgresoParticipanteHabitsPort progresoPort, LoadHabitoPort loadHabitoPort,
                                     LoadPreferenciaHorarioPort loadPreferenciaPort, SavePreferenciaHorarioPort savePreferenciaPort,
                                     CatalogoAplicable catalogoAplicable, Clock clock) {
        this.progresoPort = progresoPort;
        this.loadHabitoPort = loadHabitoPort;
        this.loadPreferenciaPort = loadPreferenciaPort;
        this.savePreferenciaPort = savePreferenciaPort;
        this.catalogoAplicable = catalogoAplicable;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OfertaDeRepeticion> ofrecer(UserId actorId) {
        ProgresoParticipanteHabits progreso = requireProgreso(actorId);
        LocalDate hoy = hoyDe(progreso);
        SemanaCalendaria modelo = SemanaCalendaria.de(hoy);
        SemanaCalendaria destino = modelo.siguiente();
        if (!estaDentroDelPrograma(progreso, hoy, destino)) {
            return Optional.empty();
        }
        Contexto contexto = cargar(actorId, progreso, modelo, destino, hoy);
        boolean modeloConCambiosPropios = contexto.filasModelo.stream().anyMatch(f -> contexto.aplicables.containsKey(f.habitoId()));
        if (!modeloConCambiosPropios || contexto.plan.sinCambios()) {
            return Optional.empty();
        }
        return Optional.of(new OfertaDeRepeticion(modelo, destino, RepeticionDeSemana.huella(contexto.filasModelo, contexto.aplicables.keySet()),
                contexto.plan.reemplazaLoPropio(), habitosDe(contexto), excluidosDe(contexto.plan)));
    }

    @Override
    @Transactional
    public ResultadoDeRepeticion repetir(UserId actorId, SemanaCalendaria modelo, SemanaCalendaria destino, boolean reemplazar) {
        return repetir(actorId, modelo, destino, reemplazar, clock.now());
    }

    @Override
    @Transactional
    public ResultadoDeRepeticion repetir(UserId actorId, SemanaCalendaria modelo, SemanaCalendaria destino, boolean reemplazar,
                                         Instant ahora) {
        ProgresoParticipanteHabits progreso = requireProgreso(actorId);
        LocalDate hoy = hoyDe(progreso, ahora);
        if (modelo.equals(destino)) {
            throw new IllegalArgumentException("La semana que se repite y la que queda igual no pueden ser la misma");
        }
        if (!destino.tieneDiasPorCambiar(hoy)) {
            throw new IllegalArgumentException("Esa semana ya pasó: no queda ningún día que se pueda cambiar");
        }
        Contexto contexto = cargar(actorId, progreso, modelo, destino, hoy);
        if (contexto.plan.reemplazaLoPropio() && !reemplazar) {
            throw new IllegalStateException("Esa semana ya tiene cambios tuyos que se reemplazarían. Confirma para reemplazarlos.");
        }
        savePreferenciaPort.guardarFechasPropias(actorId, contexto.plan.escrituras(), ahora);
        savePreferenciaPort.borrarParaFechas(actorId, contexto.plan.borrados().stream()
                .map(b -> new SavePreferenciaHorarioPort.FechaABorrar(b.habitoId(), b.fecha())).toList());
        int habitos = (int) contexto.plan.cambios().stream().map(CambioPlaneado::habitoId).distinct().count();
        return new ResultadoDeRepeticion(destino, contexto.plan.escrituras().size(), contexto.plan.borrados().size(), habitos,
                excluidosDe(contexto.plan));
    }

    @Override
    @Transactional
    public int deshacerSemana(UserId actorId, SemanaCalendaria semana) {
        return deshacerSemana(actorId, semana, clock.now());
    }

    @Override
    @Transactional
    public int deshacerSemana(UserId actorId, SemanaCalendaria semana, Instant ahora) {
        ProgresoParticipanteHabits progreso = requireProgreso(actorId);
        LocalDate hoy = hoyDe(progreso, ahora);
        LocalDate desde = semana.lunes().isAfter(hoy) ? semana.lunes() : hoy.plusDays(1);
        return desde.isAfter(semana.domingo()) ? 0 : savePreferenciaPort.borrarFechasPropias(actorId, Set.of(), desde, semana.domingo());
    }

    /** Todo lo que el plan necesita, leido de una vez. */
    private record Contexto(Map<HabitoId, Habito> aplicables, List<FechaPropia> filasModelo, PlanDeRepeticion plan,
                            SemanaCalendaria destino) {
    }

    private Contexto cargar(UserId actorId, ProgresoParticipanteHabits progreso, SemanaCalendaria modelo, SemanaCalendaria destino,
                            LocalDate hoy) {
        Map<HabitoId, Habito> aplicables = catalogoAplicable.aplicablesPara(actorId, progreso, destino.lunes()).stream()
                .collect(Collectors.toMap(Habito::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<FechaPropia> filasModelo = loadPreferenciaPort.fechasPropiasEntre(actorId, modelo.lunes(), modelo.domingo());
        List<FechaPropia> filasDestino = loadPreferenciaPort.fechasPropiasEntre(actorId, destino.lunes(), destino.domingo());
        PlanDeRepeticion plan = RepeticionDeSemana.planear(modelo, destino, filasModelo, filasDestino, aplicables.keySet(), hoy);
        return new Contexto(aplicables, filasModelo, plan, destino);
    }

    private List<HabitoDeLaOferta> habitosDe(Contexto contexto) {
        Map<HabitoId, List<CambioPlaneado>> porHabito = contexto.plan.cambios().stream()
                .collect(Collectors.groupingBy(CambioPlaneado::habitoId, LinkedHashMap::new, Collectors.toList()));
        return porHabito.entrySet().stream()
                .map(e -> new HabitoDeLaOferta(e.getKey(), contexto.aplicables.get(e.getKey()).titulo(),
                        e.getValue().stream().sorted(Comparator.comparing(CambioPlaneado::fecha)).map(RepeticionDeSemanaService::diaDe).toList()))
                .sorted(Comparator.comparing(HabitoDeLaOferta::titulo, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static DiaDeLaOferta diaDe(CambioPlaneado cambio) {
        FechaPropia fila = cambio.fila();
        if (fila == null) {
            return new DiaDeLaOferta(cambio.fecha(), cambio.fecha().getDayOfWeek(), true, null, null, null, null, cambio.reemplaza(), true);
        }
        var aviso = fila.recordatorio();
        CombinacionDeAlarma alarma = fila.alarma() == null ? null : fila.alarma().aplicadoA(CombinacionDeAlarma.COMO_HOY);
        return new DiaDeLaOferta(cambio.fecha(), cambio.fecha().getDayOfWeek(), fila.activo(), fila.horaDisparo(),
                aviso == null ? null : aviso.activo(), aviso == null ? null : aviso.antelaciones(), alarma, cambio.reemplaza(), false);
    }

    private List<HabitoExcluido> excluidosDe(PlanDeRepeticion plan) {
        if (plan.excluidos().isEmpty()) {
            return List.of();
        }
        return loadHabitoPort.porIds(plan.excluidos()).stream()
                .map(h -> new HabitoExcluido(h.id(), h.titulo(), HabitoExcluido.NO_DISPONIBLE))
                .sorted(Comparator.comparing(HabitoExcluido::titulo, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * La semana siguiente tiene que empezar dentro del programa de ESTA persona (90 o 365 dias, spec 022): no se ofrece repetir una semana que ya no
     * existe. Con un tope fijo de 90, el Diplomado de 1 ano perdia la oferta a partir del dia 84.
     */
    private static boolean estaDentroDelPrograma(ProgresoParticipanteHabits progreso, LocalDate hoy, SemanaCalendaria destino) {
        if (progreso.diaPrograma() < 1) {
            return false;
        }
        long diasHastaElDestino = ChronoUnit.DAYS.between(hoy, destino.lunes());
        return progreso.diaPrograma() + diasHastaElDestino <= progreso.duracionDias();
    }

    private LocalDate hoyDe(ProgresoParticipanteHabits progreso) {
        return hoyDe(progreso, clock.now());
    }

    private static LocalDate hoyDe(ProgresoParticipanteHabits progreso, Instant ahora) {
        return ahora.atZone(ZoneId.of(progreso.timezone())).toLocalDate();
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
