package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.audiencia.CargarAudienciasPort;
import com.renaser.os.habits.application.ports.out.audiencia.GuardarAudienciaPort;
import com.renaser.os.habits.domain.model.audiencia.HistorialDeAudiencia;
import com.renaser.os.habits.domain.model.audiencia.VersionDeAudiencia;
import com.renaser.os.habits.domain.model.habito.HabitoId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Doble en memoria de {@code audiencias_habito}, append-only como la tabla (spec 001). */
final class AudienciasEnMemoria implements CargarAudienciasPort, GuardarAudienciaPort {

    final List<VersionDeAudiencia> versiones = new ArrayList<>();

    @Override
    public Map<HabitoId, HistorialDeAudiencia> deHabitos(Collection<HabitoId> habitos) {
        return versiones.stream().filter(v -> habitos.contains(v.habitoId()))
                .collect(Collectors.groupingBy(VersionDeAudiencia::habitoId)).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> HistorialDeAudiencia.de(e.getValue())));
    }

    @Override
    public void agregarVersion(VersionDeAudiencia version) {
        versiones.add(version);
    }
}
