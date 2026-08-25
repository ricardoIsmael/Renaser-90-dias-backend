package com.renaser.os.rocks.infrastructure.adapter.out.persistence.coherencia;

import com.renaser.os.rocks.application.ports.out.coherencia.CargarConteoDiarioRocasPort;
import com.renaser.os.rocks.domain.model.coherencia.DiaRocas;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * D-43: traduce la fila cruda ({@code Object[]}: participanteId, fecha,
 * total, completadas — mismo estilo de proyección que
 * {@code academy.SpringDataRecursoLeccionRepository#contarPorLecciones})
 * devuelta por {@link SpringDataConteoDiarioRocasRepository} a
 * {@link DiaRocas}, agrupando por participante. UNA sola consulta para todos
 * los {@code participantes} pedidos, nunca un bucle.
 */
@Component
class CargarConteoDiarioRocasPersistenceAdapter implements CargarConteoDiarioRocasPort {

    private final SpringDataConteoDiarioRocasRepository repository;

    CargarConteoDiarioRocasPersistenceAdapter(SpringDataConteoDiarioRocasRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<UserId, List<DiaRocas>> conteoDiarioPorParticipante(Collection<UserId> participantes, LocalDate desde, LocalDate hasta) {
        List<UUID> ids = participantes.stream().map(UserId::value).toList();
        List<Object[]> filas = repository.conteoDiarioPorParticipante(ids, desde, hasta);

        Map<UserId, List<DiaRocas>> resultado = new LinkedHashMap<>();
        for (Object[] fila : filas) {
            UserId participante = UserId.of((UUID) fila[0]);
            LocalDate fecha = (LocalDate) fila[1];
            int total = ((Number) fila[2]).intValue();
            int completadas = ((Number) fila[3]).intValue();
            resultado.computeIfAbsent(participante, key -> new ArrayList<>())
                    .add(new DiaRocas(fecha, total, completadas));
        }
        return resultado;
    }
}
