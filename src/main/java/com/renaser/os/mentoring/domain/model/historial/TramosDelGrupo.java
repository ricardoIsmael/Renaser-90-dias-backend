package com.renaser.os.mentoring.domain.model.historial;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Junta los tramos de un mismo grupo para «Mis grupos anteriores» (spec 057, FR-020). Un mentor quitado y vuelto a poner en su grupo, o una
 * asignación que se cierra y se reabre el mismo día, deja varios tramos casi pegados: para la persona es UN solo paso por el grupo. Puro, sin
 * Spring ni reloj.
 */
public final class TramosDelGrupo {

    /** Dos tramos del mismo grupo con a lo sumo este número de días entre uno y otro se juntan (0 = pegados o solapados). */
    public static final int DIAS_DE_SEPARACION_QUE_SE_JUNTAN = 1;

    private TramosDelGrupo() {
    }

    /**
     * @param desde       instante en que empezó a acompañar
     * @param hasta       instante en que dejó de acompañar; null si el tramo sigue abierto
     * @param primerDia   primer día local en que lo acompañó
     * @param ultimoDia   último día local; en un tramo que sigue en curso, hoy
     * @param enCurso     el grupo sigue corriendo y el mentor lo acompaña hoy: es uno de sus grupos activos, no uno anterior
     */
    public record Tramo(UUID grupoId, String nombre, Instant desde, Instant hasta, LocalDate primerDia, LocalDate ultimoDia, boolean enCurso) {
    }

    /**
     * Cada grupo por separado: ordena por inicio y junta los que se tocan, se solapan o están separados por 1 día o menos. El resultado toma
     * el inicio más temprano y el fin más tardío, y queda {@code enCurso} si alguno lo estaba. Conserva el orden de aparición de los grupos.
     */
    public static List<Tramo> juntar(List<Tramo> tramos) {
        Map<UUID, List<Tramo>> porGrupo = new LinkedHashMap<>();
        tramos.forEach(t -> porGrupo.computeIfAbsent(t.grupoId(), g -> new ArrayList<>()).add(t));
        List<Tramo> juntos = new ArrayList<>();
        porGrupo.values().forEach(delGrupo -> juntos.addAll(juntarDeUnGrupo(delGrupo)));
        return juntos;
    }

    private static List<Tramo> juntarDeUnGrupo(List<Tramo> delGrupo) {
        List<Tramo> ordenados = delGrupo.stream().sorted(Comparator.comparing(Tramo::primerDia).thenComparing(Tramo::desde)).toList();
        List<Tramo> juntos = new ArrayList<>();
        Tramo actual = ordenados.getFirst();
        for (Tramo siguiente : ordenados.subList(1, ordenados.size())) {
            if (!siguiente.primerDia().isAfter(actual.ultimoDia().plusDays(DIAS_DE_SEPARACION_QUE_SE_JUNTAN + 1L))) {
                actual = unir(actual, siguiente);
            } else {
                juntos.add(actual);
                actual = siguiente;
            }
        }
        juntos.add(actual);
        return juntos;
    }

    private static Tramo unir(Tramo a, Tramo b) {
        Instant hasta = a.hasta() == null || b.hasta() == null ? null : a.hasta().isAfter(b.hasta()) ? a.hasta() : b.hasta();
        return new Tramo(a.grupoId(), a.nombre(), a.desde().isBefore(b.desde()) ? a.desde() : b.desde(), hasta, a.primerDia(),
                a.ultimoDia().isAfter(b.ultimoDia()) ? a.ultimoDia() : b.ultimoDia(), a.enCurso() || b.enCurso());
    }
}
