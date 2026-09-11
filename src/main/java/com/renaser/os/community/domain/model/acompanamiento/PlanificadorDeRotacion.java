package com.renaser.os.community.domain.model.acompanamiento;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Calcula el conjunto completo de cambios de una rotación antes de tocar nada.
 *
 * <p>Es puro y determinista: la misma entrada da el mismo plan, sin importar en qué orden la
 * base devolvió los grupos. De ahí sale la idempotencia — un job que se cayó a la mitad y
 * vuelve a correr recalcula exactamente el mismo plan y las claves de operación ya usadas
 * hacen que los pasos hechos no se repitan.
 *
 * <p>Se planifica todo junto y recién después se aplica, porque una rotación es un conjunto:
 * el intercambio A↔B no puede quedar a medias sin dejar a un grupo sin mentor y a otro con
 * dos (plan.md §5).
 */
public final class PlanificadorDeRotacion {

    private PlanificadorDeRotacion() {
    }

    public enum ModoRotacion {
        /** Hay al menos tantos mentores como grupos: se rota de verdad. */
        ROTACION,
        /**
         * Hay menos mentores que grupos. No se rota: solo se cubre lo que se puede.
         * Rotar acá movería el hueco de un grupo a otro — todos pierden a su mentor y
         * ninguno gana cobertura. El costo es real y el beneficio, cero.
         */
        SOLO_COBERTURA
    }

    /**
     * @param mentorSaliente quién estaba, o {@code null} si el grupo no tenía a nadie.
     * @param mentorEntrante quién queda. Nunca {@code null}: un cambio sin entrante no es un
     *                       cambio, es un grupo que se queda sin cobertura y va en su lista.
     */
    public record CambioDeMentor(CelulaId grupo, UserId mentorSaliente, UserId mentorEntrante,
                                  String claveOperacion) {
    }

    /**
     * @param gruposSinSustituto  tenían mentor y no hay con quién relevarlo. Se conserva al
     *                            actual y la rotación queda pendiente (P-03).
     * @param gruposSinCobertura  quedan sin mentor. Soporte cubre; no se finge a nadie.
     * @param mentoresQueDescansan salen de rotación este período sin grupo asignado.
     */
    public record PlanDeRotacion(ModoRotacion modo, List<CambioDeMentor> cambios,
                                  List<CelulaId> gruposSinSustituto, List<CelulaId> gruposSinCobertura,
                                  List<UserId> mentoresQueDescansan, String claveOperacion) {

        public boolean vacio() {
            return cambios.isEmpty();
        }
    }

    /**
     * @param gruposRegulares    grupos REGULAR de la cohorte. El orden de entrada no importa:
     *                           se ordena por id antes de decidir nada.
     * @param mentorPorGrupo     mentor vigente de cada grupo, si tiene.
     * @param mentoresLibres     elegibles sin grupo regular vigente. Se ordenan por id.
     * @param claveOperacion     identifica el período; de acá salen las claves por cambio.
     */
    public static PlanDeRotacion planificar(List<CelulaId> gruposRegulares, Map<CelulaId, UserId> mentorPorGrupo,
                                             List<UserId> mentoresLibres, String claveOperacion) {
        Objects.requireNonNull(claveOperacion, "claveOperacion es obligatoria");

        List<CelulaId> grupos = gruposRegulares.stream()
                .sorted(Comparator.comparing(g -> g.value().toString()))
                .toList();
        if (grupos.isEmpty()) {
            return new PlanDeRotacion(ModoRotacion.ROTACION, List.of(), List.of(), List.of(), List.of(),
                    claveOperacion);
        }

        Deque<UserId> banca = mentoresLibres.stream()
                .sorted(Comparator.comparing(m -> m.value().toString()))
                .collect(ArrayDeque::new, ArrayDeque::add, ArrayDeque::addAll);

        long conMentor = grupos.stream().filter(mentorPorGrupo::containsKey).count();
        int disponibles = (int) conMentor + banca.size();

        if (disponibles < grupos.size()) {
            return soloCobertura(grupos, mentorPorGrupo, banca, claveOperacion);
        }
        return rotar(grupos, mentorPorGrupo, banca, claveOperacion);
    }

    /**
     * No alcanzan los mentores. Se rellenan los grupos descubiertos desde la banca, en orden,
     * y el resto queda explícitamente sin cobertura. Nadie que ya tenga mentor lo pierde.
     */
    private static PlanDeRotacion soloCobertura(List<CelulaId> grupos, Map<CelulaId, UserId> mentorPorGrupo,
                                                 Deque<UserId> banca, String claveOperacion) {
        List<CambioDeMentor> cambios = new ArrayList<>();
        List<CelulaId> sinCobertura = new ArrayList<>();

        for (CelulaId grupo : grupos) {
            if (mentorPorGrupo.containsKey(grupo)) {
                continue;
            }
            if (banca.isEmpty()) {
                sinCobertura.add(grupo);
            } else {
                cambios.add(new CambioDeMentor(grupo, null, banca.poll(), claveDe(claveOperacion, grupo)));
            }
        }
        return new PlanDeRotacion(ModoRotacion.SOLO_COBERTURA, List.copyOf(cambios), List.of(),
                List.copyOf(sinCobertura), List.copyOf(banca), claveOperacion);
    }

    /**
     * Rotación cíclica de una posición sobre la secuencia [mentor de cada grupo, en orden de
     * grupo] ++ banca. Con dos grupos da el intercambio A↔B; con banca, el saliente descansa
     * y entra alguien nuevo.
     */
    private static PlanDeRotacion rotar(List<CelulaId> grupos, Map<CelulaId, UserId> mentorPorGrupo,
                                         Deque<UserId> banca, String claveOperacion) {
        // Un grupo sin mentor se cubre primero desde la banca: entra a la rotacion con
        // alguien, en vez de arrastrar un hueco por toda la secuencia.
        List<UserId> secuencia = new ArrayList<>();
        for (CelulaId grupo : grupos) {
            UserId actual = mentorPorGrupo.get(grupo);
            secuencia.add(actual != null ? actual : banca.poll());
        }
        secuencia.addAll(banca);

        List<CambioDeMentor> cambios = new ArrayList<>();
        List<CelulaId> sinSustituto = new ArrayList<>();
        List<UserId> asignados = new ArrayList<>();

        for (int i = 0; i < grupos.size(); i++) {
            CelulaId grupo = grupos.get(i);
            UserId saliente = mentorPorGrupo.get(grupo);
            UserId entrante = secuencia.get((i + 1) % secuencia.size());

            if (Objects.equals(saliente, entrante)) {
                // Le tocaria el mismo: no hay relevo posible. Se conserva y queda pendiente.
                sinSustituto.add(grupo);
                asignados.add(saliente);
                continue;
            }
            cambios.add(new CambioDeMentor(grupo, saliente, entrante, claveDe(claveOperacion, grupo)));
            asignados.add(entrante);
        }

        List<UserId> descansan = secuencia.stream().filter(Objects::nonNull).filter(m -> !asignados.contains(m))
                .toList();
        return new PlanDeRotacion(ModoRotacion.ROTACION, List.copyOf(cambios), List.copyOf(sinSustituto),
                List.of(), descansan, claveOperacion);
    }

    private static String claveDe(String claveOperacion, CelulaId grupo) {
        return claveOperacion + ":" + grupo.value();
    }
}
