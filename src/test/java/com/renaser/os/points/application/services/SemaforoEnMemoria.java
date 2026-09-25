package com.renaser.os.points.application.services;

import com.renaser.os.points.application.ports.out.semaforo.CargarDiasDelSemaforoPort;
import com.renaser.os.points.application.ports.out.semaforo.GuardarDiasDelSemaforoPort;
import com.renaser.os.points.application.ports.out.semaforo.PausasDelSemaforoPort;
import com.renaser.os.points.application.ports.out.semaforo.SemanasDelSemaforoPort;
import com.renaser.os.points.domain.model.semaforo.CumplimientoDelDia;
import com.renaser.os.points.domain.model.semaforo.FotoSemanal;
import com.renaser.os.points.domain.model.semaforo.PausaDeMedicion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Las tres tablas del semáforo en memoria, con la misma semántica que los adaptadores JDBC. */
class SemaforoEnMemoria implements CargarDiasDelSemaforoPort, GuardarDiasDelSemaforoPort, SemanasDelSemaforoPort,
        PausasDelSemaforoPort {

    final Map<UserId, TreeMap<LocalDate, CumplimientoDelDia>> dias = new HashMap<>();
    final Map<UserId, Instant> ultimoCalculo = new HashMap<>();
    final Map<UserId, TreeMap<LocalDate, FotoSemanal>> semanas = new HashMap<>();
    final Map<UserId, List<PausaDeMedicion>> pausas = new HashMap<>();
    final Set<UserId> fallanAlGuardar = new HashSet<>();

    @Override
    public Map<UserId, Map<LocalDate, CumplimientoDelDia>> entre(Collection<UserId> participantes, LocalDate desde,
                                                                 LocalDate hasta) {
        Map<UserId, Map<LocalDate, CumplimientoDelDia>> resultado = new LinkedHashMap<>();
        for (UserId id : participantes) {
            TreeMap<LocalDate, CumplimientoDelDia> suyos = dias.get(id);
            if (suyos != null && !suyos.subMap(desde, true, hasta, true).isEmpty()) {
                resultado.put(id, new TreeMap<>(suyos.subMap(desde, true, hasta, true)));
            }
        }
        return resultado;
    }

    @Override
    public Optional<Instant> ultimoCalculoDe(UserId participante) {
        return Optional.ofNullable(ultimoCalculo.get(participante));
    }

    @Override
    public int guardar(UserId participante, Collection<CumplimientoDelDia> nuevos, Instant calculadoEn) {
        if (fallanAlGuardar.contains(participante)) {
            throw new IllegalStateException("falla simulada");
        }
        TreeMap<LocalDate, CumplimientoDelDia> suyos = dias.computeIfAbsent(participante, k -> new TreeMap<>());
        int cambiados = 0;
        for (CumplimientoDelDia dia : nuevos) {
            if (!Objects.equals(suyos.put(dia.fecha(), dia), dia)) {
                cambiados++;
            }
        }
        if (cambiados > 0) {
            ultimoCalculo.put(participante, calculadoEn);
        }
        return cambiados;
    }

    @Override
    public Map<UserId, LocalDate> ultimaCerradaDe(Collection<UserId> participantes) {
        Map<UserId, LocalDate> resultado = new LinkedHashMap<>();
        participantes.forEach(id -> Optional.ofNullable(semanas.get(id)).filter(m -> !m.isEmpty())
                .ifPresent(m -> resultado.put(id, m.lastKey())));
        return resultado;
    }

    @Override
    public Map<UserId, FotoSemanal> deLaSemana(Collection<UserId> participantes, LocalDate semanaHasta) {
        Map<UserId, FotoSemanal> resultado = new LinkedHashMap<>();
        participantes.forEach(id -> Optional.ofNullable(semanas.get(id)).map(m -> m.get(semanaHasta))
                .ifPresent(foto -> resultado.put(id, foto)));
        return resultado;
    }

    @Override
    public List<FotoSemanal> ultimasDe(UserId participante, int cantidad) {
        List<FotoSemanal> todas = new ArrayList<>(semanas.getOrDefault(participante, new TreeMap<>()).values());
        return todas.subList(Math.max(0, todas.size() - cantidad), todas.size());
    }

    @Override
    public boolean registrar(UserId participante, FotoSemanal foto) {
        return semanas.computeIfAbsent(participante, k -> new TreeMap<>()).putIfAbsent(foto.semanaHasta(), foto) == null;
    }

    @Override
    public Map<UserId, List<PausaDeMedicion>> de(Collection<UserId> usuarios) {
        Map<UserId, List<PausaDeMedicion>> resultado = new LinkedHashMap<>();
        usuarios.forEach(id -> Optional.ofNullable(pausas.get(id)).ifPresent(p -> resultado.put(id, List.copyOf(p))));
        return resultado;
    }

    @Override
    public void guardar(PausaDeMedicion pausa) {
        List<PausaDeMedicion> suyas = pausas.computeIfAbsent(pausa.usuarioId(), k -> new ArrayList<>());
        suyas.removeIf(p -> p.id().equals(pausa.id()));
        suyas.add(pausa);
        suyas.sort(Comparator.comparing(PausaDeMedicion::desde));
    }
}
