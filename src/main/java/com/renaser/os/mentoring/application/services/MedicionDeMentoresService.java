package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.mentoring.api.MedicionDeMentoresFinder;
import com.renaser.os.mentoring.domain.model.semaforo.MedicionDelAprendiz;
import com.renaser.os.mentoring.domain.model.semaforo.PeriodoDelSemaforo;
import com.renaser.os.mentoring.domain.model.semaforo.ResumenDelGrupo;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementación de {@link MedicionDeMentoresFinder}. Sin autorización propia: la decide quien la
 * expone (la gestión del Líder de Mentores).
 *
 * <p>Mismo recorrido que {@code SemaforoPorGruposService}: los grupos regulares con mentor vigente, el
 * padrón de {@link MedicionDeGrupos} y UNA lectura del semáforo para todos los aprendices juntos.
 */
@Service
public class MedicionDeMentoresService implements MedicionDeMentoresFinder {

    private static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("America/Lima");

    private final MedicionDeGrupos medicion;
    private final AcompanamientoFinder acompanamientoFinder;
    private final Clock clock;

    MedicionDeMentoresService(MedicionDeGrupos medicion, AcompanamientoFinder acompanamientoFinder, Clock clock) {
        this.medicion = medicion;
        this.acompanamientoFinder = acompanamientoFinder;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public MedicionVigente vigente() {
        Instant ahora = clock.now();
        List<GrupoAcompanado> grupos = acompanamientoFinder.gruposConMentorVigente(ahora);
        Map<UUID, List<UserId>> aprendicesPorGrupo = medicion.aprendicesPorGrupo(grupos, ahora);
        Map<UserId, VentanaDelSemaforo> ventanas = medicion.ventanasDe(todos(aprendicesPorGrupo.values()), null);

        List<GrupoMedido> medidos = grupos.stream()
                .map(grupo -> medir(grupo, aprendicesPorGrupo.get(grupo.grupoId()), ventanas))
                .toList();
        ZoneId zona = grupos.isEmpty() ? ZONA_POR_DEFECTO : ZoneId.of(grupos.getFirst().zonaHoraria());
        PeriodoDelSemaforo periodo = PeriodoDelSemaforo.de(ventanas.values(),
                PeriodoDelSemaforo.esperado(ahora.atZone(zona).toLocalDate(), null));
        return new MedicionVigente(periodo.desde(), periodo.hasta(), periodo.cerrada(), medidos,
                porMentor(grupos, aprendicesPorGrupo, ventanas));
    }

    private static GrupoMedido medir(GrupoAcompanado grupo, List<UserId> aprendices,
                                     Map<UserId, VentanaDelSemaforo> ventanas) {
        return new GrupoMedido(grupo.grupoId(), grupo.nombre(), grupo.mentorId(), List.copyOf(aprendices),
                resumir(aprendices, ventanas));
    }

    /** Sobre la unión de sus grupos, sin repetir a quien está en dos (D-139): no es un promedio de promedios. */
    private static Map<UserId, SemaforoResumido> porMentor(List<GrupoAcompanado> grupos,
                                                            Map<UUID, List<UserId>> aprendicesPorGrupo,
                                                            Map<UserId, VentanaDelSemaforo> ventanas) {
        Map<UserId, Set<UserId>> aprendicesPorMentor = new LinkedHashMap<>();
        grupos.forEach(grupo -> aprendicesPorMentor.computeIfAbsent(grupo.mentorId(), m -> new LinkedHashSet<>())
                .addAll(aprendicesPorGrupo.get(grupo.grupoId())));
        Map<UserId, SemaforoResumido> resumenes = new LinkedHashMap<>();
        aprendicesPorMentor.forEach((mentor, aprendices) -> resumenes.put(mentor, resumir(aprendices, ventanas)));
        return resumenes;
    }

    private static SemaforoResumido resumir(Iterable<UserId> aprendices, Map<UserId, VentanaDelSemaforo> ventanas) {
        List<MedicionDelAprendiz> mediciones = new java.util.ArrayList<>();
        aprendices.forEach(aprendiz -> mediciones.add(MedicionDelAprendiz.de(ventanas.get(aprendiz))));
        ResumenDelGrupo resumen = ResumenDelGrupo.de(mediciones);
        ColorSemaforo color = resumen.colorDelPromedio();
        return new SemaforoResumido(resumen.conteo().verde(), resumen.conteo().amarillo(), resumen.conteo().rojo(),
                resumen.conteo().sinDatos(), resumen.promedio(), color.name(), color.etiqueta());
    }

    private static Set<UserId> todos(java.util.Collection<List<UserId>> listas) {
        return listas.stream().flatMap(List::stream).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
