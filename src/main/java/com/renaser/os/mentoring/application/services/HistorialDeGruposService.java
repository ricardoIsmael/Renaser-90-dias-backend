package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoBasico;
import com.renaser.os.community.api.AcompanamientoFinder.TramoDeAcompanamiento;
import com.renaser.os.mentoring.application.ports.in.ConsultarHistorialDeGruposUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarRankingDeGruposUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarRankingDeGruposUseCase.FilaDeGrupo;
import com.renaser.os.mentoring.domain.model.semaforo.MedicionDelAprendiz;
import com.renaser.os.mentoring.domain.model.semaforo.ResumenDelGrupo;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * «Mis grupos anteriores» (spec 057, FR-020 y FR-021). Reutiliza todo: los tramos de {@link AcompanamientoFinder}, el semáforo de
 * {@link SemaforoFinder#semanaDe}, el resumen del grupo ({@link ResumenDelGrupo}, el mismo del líder y de la tabla del mentor) y el ranking
 * mensual de {@link ConsultarRankingDeGruposUseCase}. Aquí solo se elige qué tramos son «anteriores» y de qué semana se toma el cierre.
 *
 * <p>Es anterior el tramo ya cerrado, o el abierto de un grupo que ya terminó. El color y el porcentaje son los de la última semana
 * (sábado a viernes) cerrada DENTRO del tramo —nunca los de hoy— y los nombres salen sin ids.
 */
@Service
class HistorialDeGruposService implements ConsultarHistorialDeGruposUseCase {

    private final AcompanamientoFinder acompanamiento;
    private final SemaforoFinder semaforo;
    private final UserSummaryFinder usuarios;
    private final ConsultarRankingDeGruposUseCase ranking;
    private final Clock clock;

    HistorialDeGruposService(AcompanamientoFinder acompanamiento, SemaforoFinder semaforo, UserSummaryFinder usuarios,
                             ConsultarRankingDeGruposUseCase ranking, Clock clock) {
        this.acompanamiento = acompanamiento;
        this.semaforo = semaforo;
        this.usuarios = usuarios;
        this.ranking = ranking;
        this.clock = clock;
    }

    /** Un tramo ya terminado, con sus días locales (primero y último) en la zona de su grupo. */
    private record TramoAnterior(TramoDeAcompanamiento tramo, GrupoBasico grupo, Instant hasta, LocalDate primerDia, LocalDate ultimoDia) {
    }

    @Override
    @Transactional(readOnly = true)
    public HistorialDeGrupos historialDe(UserId actorId) {
        Instant ahora = clock.now();
        Map<String, List<FilaDeGrupo>> rankings = new HashMap<>();
        List<GrupoAnterior> grupos = acompanamiento.tramosDeAcompanante(actorId, Instant.EPOCH, null).stream()
                .map(tramo -> anterior(tramo, ahora))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(TramoAnterior::ultimoDia).thenComparing(TramoAnterior::primerDia).reversed())
                .map(t -> armar(actorId, t, rankings))
                .toList();
        return new HistorialDeGrupos(grupos);
    }

    private Optional<TramoAnterior> anterior(TramoDeAcompanamiento tramo, Instant ahora) {
        Optional<GrupoBasico> grupo = acompanamiento.grupo(tramo.grupoId());
        if (grupo.isEmpty()) {
            return Optional.empty();
        }
        ZoneId zona = ZoneId.of(grupo.get().zonaHoraria());
        LocalDate primerDia = tramo.desde().atZone(zona).toLocalDate();
        if (tramo.hasta() != null && !tramo.hasta().isAfter(ahora)) {
            LocalDate ultimoDia = tramo.hasta().minusMillis(1).atZone(zona).toLocalDate();
            return ultimoDia.isBefore(primerDia) ? Optional.empty()
                    : Optional.of(new TramoAnterior(tramo, grupo.get(), tramo.hasta(), primerDia, ultimoDia));
        }
        if (tramo.hasta() != null) {
            return Optional.empty();
        }
        // Tramo abierto: solo es anterior si el grupo ya terminó (un grupo programado no lo es).
        return acompanamiento.finDelGrupoYaTerminado(tramo.grupoId(), ahora)
                .filter(fin -> !fin.isBefore(primerDia))
                .map(fin -> new TramoAnterior(tramo, grupo.get(), fin.plusDays(1).atStartOfDay(zona).toInstant(), primerDia, fin));
    }

    private GrupoAnterior armar(UserId actorId, TramoAnterior t, Map<String, List<FilaDeGrupo>> rankings) {
        UUID grupoId = t.tramo().grupoId();
        Set<UserId> aprendices = new LinkedHashSet<>();
        acompanamiento.tramosDeAprendices(grupoId, t.tramo().desde(), t.hasta()).forEach(a -> aprendices.add(a.aprendizId()));
        Map<UserId, UserSummary> perfiles = aprendices.isEmpty() ? Map.of() : usuarios.findByIds(aprendices);
        List<UserId> visibles = aprendices.stream().filter(a -> perfiles.containsKey(a) && perfiles.get(a).status().allowsAccess()).toList();

        Optional<LocalDate> cierre = ultimaSemanaCerradaDentro(t);
        Map<UserId, VentanaDelSemaforo> ventanas = cierre.isPresent() && !visibles.isEmpty()
                ? semaforo.semanaDe(visibles, cierre.get()) : Map.of();
        List<MedicionDelAprendiz> mediciones = visibles.stream().map(a -> MedicionDelAprendiz.de(ventanas.get(a))).toList();
        ResumenDelGrupo resumen = ResumenDelGrupo.de(mediciones);
        List<PersonaAnterior> personas = new ArrayList<>();
        for (int i = 0; i < visibles.size(); i++) {
            personas.add(new PersonaAnterior(perfiles.get(visibles.get(i)).fullName(), mediciones.get(i).color().name()));
        }
        personas.sort(Comparator.comparing(PersonaAnterior::nombre, Comparator.nullsLast(Comparator.naturalOrder())));
        return new GrupoAnterior(grupoId, t.tramo().grupoNombre(), t.primerDia(), t.ultimoDia(), aprendices.size(),
                puestoFinal(actorId, t, rankings), (cierre.isPresent() ? resumen.colorDelPromedio() : ColorSemaforo.SIN_DATOS).name(),
                cierre.isPresent() ? resumen.promedio() : null, personas);
    }

    /** El viernes que cierra la última semana (sábado a viernes) que terminó dentro del tramo; vacío si el tramo no alcanzó a cerrar ninguna. */
    private static Optional<LocalDate> ultimaSemanaCerradaDentro(TramoAnterior t) {
        LocalDate viernes = t.ultimoDia().with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
        return viernes.isBefore(t.primerDia()) ? Optional.empty() : Optional.of(viernes);
    }

    /** La posición de su grupo en el ranking del último mes del tramo; null si ese mes no lo midió. */
    private PuestoFinal puestoFinal(UserId actorId, TramoAnterior t, Map<String, List<FilaDeGrupo>> rankings) {
        YearMonth mes = YearMonth.from(t.ultimoDia());
        UUID cohorte = t.grupo().cohorteId();
        List<FilaDeGrupo> filas = rankings.computeIfAbsent(cohorte + "|" + mes,
                k -> ranking.ranking(actorId, cohorte, mes).grupos());
        return filas.stream().filter(f -> f.grupoId().equals(t.tramo().grupoId()) && f.porcentaje() != null).findFirst()
                .map(f -> new PuestoFinal(f.posicion(), (int) filas.stream().filter(o -> o.porcentaje() != null).count()))
                .orElse(null);
    }
}
