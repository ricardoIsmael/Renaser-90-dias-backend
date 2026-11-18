package com.renaser.os.community.application.services;

import com.renaser.os.community.api.CupoDeLosGruposFinder;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * El tope de aprendices de cada grupo (spec 056, FR-003). El cupo lo calcula el agregado ({@code Celula.cupo}), no esta clase: aquí solo se
 * juntan el grupo y la capacidad de la política de su cohorte, que se lee una vez por cohorte. Los grupos son decenas, así que se leen
 * todos de una vez y se filtran en memoria, como {@code GruposPorFormacionService}.
 */
@Service
class CupoDeLosGruposService implements CupoDeLosGruposFinder {

    private final LoadCelulaPort celulas;
    private final LoadPoliticaMentoriaPort politicas;

    CupoDeLosGruposService(LoadCelulaPort celulas, LoadPoliticaMentoriaPort politicas) {
        this.celulas = celulas;
        this.politicas = politicas;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> maximosDe(Collection<UUID> grupoIds) {
        if (grupoIds == null || grupoIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> pedidos = Set.copyOf(grupoIds);
        Map<CohorteId, Integer> capacidadPorCohorte = new HashMap<>();
        Map<UUID, Integer> maximos = new HashMap<>();
        celulas.todas().stream().filter(celula -> pedidos.contains(celula.id().value())).forEach(celula -> {
            int capacidad = capacidadPorCohorte.computeIfAbsent(celula.cohorteId(), this::capacidadDeLaPolitica);
            celula.cupo(capacidad).maximo().ifPresent(maximo -> maximos.put(celula.id().value(), maximo));
        });
        return maximos;
    }

    /** Sin fila de política (una cohorte creada después de V45), la de por defecto: la ausencia no es un error. */
    private int capacidadDeLaPolitica(CohorteId cohorte) {
        return politicas.porCohorte(cohorte).orElseGet(() -> PoliticaMentoria.porDefecto(cohorte)).capacidadCelula();
    }
}
