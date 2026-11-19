package com.renaser.os.community.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * El tope de aprendices de cada grupo, para mostrar el cupo de cada mentor (spec 056, FR-003). El cupo de un grupo regular es el que
 * tiene el grupo mismo si se le fijó uno y, si no, la capacidad de la política de su cohorte; la recepción no tiene tope (D-05).
 *
 * <p>Interfaz aparte de {@link AcompanamientoFinder} a propósito: aquella tiene dobles de prueba escritos a mano en otros módulos y sumarle
 * un método abstracto obligaba a tocarlos todos, igual que {@link MentorVigenteFinder}. Solo lectura y sin autorización propia: quien la
 * consume ya decidió quién puede mirar.
 */
public interface CupoDeLosGruposFinder {

    /**
     * El tope de aprendices de cada grupo pedido. Una consulta para todos, nunca una por grupo.
     *
     * @return por id de grupo; los que no existen y los que no tienen tope (la recepción) no figuran
     */
    Map<UUID, Integer> maximosDe(Collection<UUID> grupoIds);
}
