package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.mentoring.application.ports.in.ConsultarSemaforoPorGruposUseCase;
import com.renaser.os.mentoring.domain.model.semaforo.ConteoPorColor;
import com.renaser.os.mentoring.domain.model.semaforo.MedicionDelAprendiz;
import com.renaser.os.mentoring.domain.model.semaforo.OrdenDelSemaforo;
import com.renaser.os.mentoring.domain.model.semaforo.PeriodoDelSemaforo;
import com.renaser.os.mentoring.domain.model.semaforo.ResumenDelGrupo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * El semáforo de todos los grupos, sin nombres de aprendices: lo que ve el líder de mentores y
 * también el administrador y el alquimista (docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §4.4).
 *
 * <p>Los aprendices se piden grupo por grupo —son pocos grupos—, pero el semáforo se lee UNA vez
 * para todos juntos y los nombres que se piden son solo los de los mentores. Cada grupo se resume
 * con {@link ResumenDelGrupo}, lo mismo que el encabezado de la tabla del mentor.
 */
@Service
public class SemaforoPorGruposService implements ConsultarSemaforoPorGruposUseCase {

    /** Referencia del encabezado cuando no hay ningún grupo del que tomar la zona. */
    private static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("America/Lima");

    private final AccesoAVistasDelSemaforo acceso;
    private final MedicionDeGrupos medicion;
    private final AcompanamientoFinder acompanamientoFinder;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;

    SemaforoPorGruposService(AccesoAVistasDelSemaforo acceso, MedicionDeGrupos medicion,
                             AcompanamientoFinder acompanamientoFinder, UserSummaryFinder userSummaryFinder,
                             Clock clock) {
        this.acceso = acceso;
        this.medicion = medicion;
        this.acompanamientoFinder = acompanamientoFinder;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenPorGrupos resumenDe(ConsultaResumenPorGrupos consulta) {
        acceso.requireLiderazgoActivo(consulta.actorId());
        Instant ahora = clock.now();
        List<GrupoAcompanado> grupos = gruposDe(consulta.semanaHasta(), ahora);
        Map<UUID, List<UserId>> aprendicesPorGrupo =
                medicion.aprendicesPorGrupo(grupos,
                        instanteDelPadron(consulta.semanaHasta(), ahora, zonaDeReferencia(grupos)));
        Map<UserId, VentanaDelSemaforo> ventanas =
                medicion.ventanasDe(todos(aprendicesPorGrupo), consulta.semanaHasta());

        List<GrupoDelResumen> resumenes = resumir(grupos, aprendicesPorGrupo, ventanas);
        PeriodoDelSemaforo esperado = PeriodoDelSemaforo.esperado(
                ahora.atZone(zonaDeReferencia(grupos)).toLocalDate(), consulta.semanaHasta());
        return new ResumenPorGrupos(PeriodoDelSemaforo.de(ventanas.values(), esperado), totales(resumenes),
                resumenes);
    }

    /**
     * Los grupos regulares EN CURSO del periodo, con mentor o sin él (D-240, E-478). La recepción
     * queda fuera, igual que en el ranking.
     *
     * <p>> **Corregido 2026-10-01.** Eran {@code gruposConMentorVigente(ahora)} para cualquier semana:
     * un grupo sin mentor no se veía nunca, y la semana pasada se armaba con los grupos de HOY (uno
     * cerrado el domingo desaparecía de su propia semana; uno que empezó hoy aparecía en una semana en
     * la que no existía).
     */
    private List<GrupoAcompanado> gruposDe(LocalDate semanaHasta, Instant ahora) {
        if (semanaHasta == null) {
            return acompanamientoFinder.gruposRegularesEnCurso(ahora);
        }
        return acompanamientoFinder.gruposRegularesEnCursoEntre(semanaHasta.minusDays(6), semanaHasta);
    }

    /**
     * A qué instante se pregunta quién estaba en cada grupo: ahora para la semana en curso, y el
     * último instante de la semana para una pasada. Un solo instante y no «alguna vez en la semana»:
     * quien pasó de un grupo a otro a mitad de semana cuenta en el grupo donde la terminó y no en los
     * dos. El que está en dos grupos A LA VEZ sí cuenta en los dos (D-139, §4.4).
     */
    private static Instant instanteDelPadron(LocalDate semanaHasta, Instant ahora, ZoneId zona) {
        if (semanaHasta == null) {
            return ahora;
        }
        Instant cierre = semanaHasta.plusDays(1).atStartOfDay(zona).toInstant().minusMillis(1);
        return ahora.isBefore(cierre) ? ahora : cierre;
    }

    /** Sin repetidos: un aprendiz puede estar en dos grupos (D-139) y se lo pide una sola vez. */
    private static Set<UserId> todos(Map<UUID, List<UserId>> aprendicesPorGrupo) {
        return aprendicesPorGrupo.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<GrupoDelResumen> resumir(List<GrupoAcompanado> grupos, Map<UUID, List<UserId>> aprendicesPorGrupo,
                                          Map<UserId, VentanaDelSemaforo> ventanas) {
        Map<UserId, UserSummary> mentores = userSummaryFinder.findByIds(grupos.stream()
                .map(GrupoAcompanado::mentorId).filter(java.util.Objects::nonNull).distinct().toList());
        return grupos.stream()
                .map(grupo -> new GrupoDelResumen(grupo.grupoId(), grupo.nombre(),
                        grupo.mentorId() == null ? null : nombreDe(mentores.get(grupo.mentorId())),
                        ResumenDelGrupo.de(medicionesDe(aprendicesPorGrupo.get(grupo.grupoId()), ventanas)),
                        grupo.mentorId() == null))
                .sorted(Comparator.comparing(GrupoDelResumen::grupoNombre, OrdenDelSemaforo.alfabetico())
                        .thenComparing(GrupoDelResumen::grupoId))
                .toList();
    }

    private static List<MedicionDelAprendiz> medicionesDe(List<UserId> aprendices,
                                                          Map<UserId, VentanaDelSemaforo> ventanas) {
        return aprendices.stream().map(aprendiz -> MedicionDelAprendiz.de(ventanas.get(aprendiz))).toList();
    }

    private static ConteoPorColor totales(List<GrupoDelResumen> resumenes) {
        return resumenes.stream()
                .map(grupo -> grupo.resumen().conteo())
                .reduce(ConteoPorColor.NINGUNO, ConteoPorColor::mas);
    }

    private static String nombreDe(UserSummary mentor) {
        return mentor == null ? null : mentor.fullName();
    }

    /**
     * La zona del primer grupo, como el ranking de grupos: los grupos comparten la del padrón. Solo
     * decide el encabezado cuando no hay ventanas de las que tomarlo.
     */
    private static ZoneId zonaDeReferencia(List<GrupoAcompanado> grupos) {
        return grupos.isEmpty() ? ZONA_POR_DEFECTO : ZoneId.of(grupos.getFirst().zonaHoraria());
    }
}
