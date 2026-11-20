package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoBasico;
import com.renaser.os.community.api.AcompanamientoFinder.TramoDeAcompanamiento;
import com.renaser.os.mentoring.application.ports.in.ConsultarHistorialDeGruposUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarRankingDeGruposUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarRankingDeGruposUseCase.FilaDeGrupo;
import com.renaser.os.mentoring.domain.model.historial.TramosDelGrupo;
import com.renaser.os.mentoring.domain.model.historial.TramosDelGrupo.Tramo;
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

    @Override
    @Transactional(readOnly = true)
    public HistorialDeGrupos historialDe(UserId actorId) {
        Instant ahora = clock.now();
        Map<UUID, GrupoBasico> grupos = new HashMap<>();
        List<Tramo> tramos = acompanamiento.tramosDeAcompanante(actorId, Instant.EPOCH, null).stream()
                .map(tramo -> aTramo(tramo, ahora, grupos))
                .flatMap(Optional::stream)
                .toList();
        Map<String, List<FilaDeGrupo>> rankings = new HashMap<>();
        List<GrupoAnterior> anteriores = TramosDelGrupo.juntar(tramos).stream()
                .filter(t -> !t.enCurso())
                .sorted(Comparator.comparing(Tramo::ultimoDia).thenComparing(Tramo::primerDia).reversed())
                .map(t -> armar(actorId, t, grupos.get(t.grupoId()), rankings))
                .flatMap(Optional::stream)
                .toList();
        return new HistorialDeGrupos(anteriores);
    }

    /**
     * El tramo con sus días locales en la zona de su grupo. Los grupos de recepción no entran (como en «Mis grupos», que no los muestra). Un
     * tramo que sigue abierto en un grupo que corre, o que termina más adelante, queda {@code enCurso}: sirve para juntar y no se lista.
     */
    private Optional<Tramo> aTramo(TramoDeAcompanamiento tramo, Instant ahora, Map<UUID, GrupoBasico> grupos) {
        if (acompanamiento.esGrupoDeRecepcion(tramo.grupoId())) {
            return Optional.empty();
        }
        Optional<GrupoBasico> grupo = acompanamiento.grupo(tramo.grupoId());
        if (grupo.isEmpty()) {
            return Optional.empty();
        }
        grupos.put(tramo.grupoId(), grupo.get());
        ZoneId zona = ZoneId.of(grupo.get().zonaHoraria());
        LocalDate primerDia = tramo.desde().atZone(zona).toLocalDate();
        if (tramo.hasta() != null && !tramo.hasta().isAfter(ahora)) {
            return conDias(tramo, tramo.hasta(), primerDia, tramo.hasta().minusMillis(1).atZone(zona).toLocalDate(), false);
        }
        Optional<LocalDate> fin = tramo.hasta() == null ? acompanamiento.finDelGrupoYaTerminado(tramo.grupoId(), ahora) : Optional.empty();
        if (fin.isPresent()) {
            // Tramo abierto de un grupo que ya terminó: el último día es el del grupo.
            return conDias(tramo, fin.get().plusDays(1).atStartOfDay(zona).toInstant(), primerDia, fin.get(), false);
        }
        return conDias(tramo, tramo.hasta(), primerDia, ahora.atZone(zona).toLocalDate(), true);
    }

    private static Optional<Tramo> conDias(TramoDeAcompanamiento tramo, Instant hasta, LocalDate primerDia, LocalDate ultimoDia, boolean enCurso) {
        return ultimoDia.isBefore(primerDia) ? Optional.empty()
                : Optional.of(new Tramo(tramo.grupoId(), tramo.grupoNombre(), tramo.desde(), hasta, primerDia, ultimoDia, enCurso));
    }

    private Optional<GrupoAnterior> armar(UserId actorId, Tramo t, GrupoBasico grupo, Map<String, List<FilaDeGrupo>> rankings) {
        UUID grupoId = t.grupoId();
        Set<UserId> aprendices = new LinkedHashSet<>();
        acompanamiento.tramosDeAprendices(grupoId, t.desde(), t.hasta()).forEach(a -> aprendices.add(a.aprendizId()));
        if (aprendices.isEmpty()) {
            // Un paso por un grupo sin nadie no dice nada de cómo cerró.
            return Optional.empty();
        }
        Map<UserId, UserSummary> perfiles = usuarios.findByIds(aprendices);
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
        return Optional.of(new GrupoAnterior(grupoId, t.nombre(), t.primerDia(), t.ultimoDia(), aprendices.size(),
                puestoFinal(actorId, t, grupo, rankings), (cierre.isPresent() ? resumen.colorDelPromedio() : ColorSemaforo.SIN_DATOS).name(),
                cierre.isPresent() ? resumen.promedio() : null, personas));
    }

    /** El viernes que cierra la última semana (sábado a viernes) que terminó dentro del tramo; vacío si el tramo no alcanzó a cerrar ninguna. */
    private static Optional<LocalDate> ultimaSemanaCerradaDentro(Tramo t) {
        LocalDate viernes = t.ultimoDia().with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
        return viernes.isBefore(t.primerDia()) ? Optional.empty() : Optional.of(viernes);
    }

    /** La posición de su grupo en el ranking del último mes del tramo; null si ese mes no lo midió. */
    private PuestoFinal puestoFinal(UserId actorId, Tramo t, GrupoBasico grupo, Map<String, List<FilaDeGrupo>> rankings) {
        YearMonth mes = YearMonth.from(t.ultimoDia());
        UUID cohorte = grupo.cohorteId();
        List<FilaDeGrupo> filas = rankings.computeIfAbsent(cohorte + "|" + mes,
                k -> ranking.ranking(actorId, cohorte, mes).grupos());
        return filas.stream().filter(f -> f.grupoId().equals(t.grupoId()) && f.porcentaje() != null).findFirst()
                .map(f -> new PuestoFinal(f.posicion(), (int) filas.stream().filter(o -> o.porcentaje() != null).count()))
                .orElse(null);
    }
}
