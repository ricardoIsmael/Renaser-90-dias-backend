package com.renaser.os.calendar.infrastructure.adapter.out.persistence.nivelmembresia;

import com.renaser.os.calendar.application.ports.out.nivelmembresia.LoadNivelMembresiaPort;
import com.renaser.os.calendar.domain.model.nivelmembresia.NivelMembresia;
import org.springframework.stereotype.Component;

import java.util.List;

/** {@code niveles_membresia} no tiene seed en el baseline — decision del dueño del proyecto
 * (BD inmutable en esta fase, ver docs/MODULO_CALENDAR.md §5): los niveles reales llegan
 * en la migracion de datos posterior. Con la tabla vacia, {@link #listar()} devuelve
 * {@code List.of()} y la audiencia NIVEL_MINIMO se resuelve sin destinatarios — nunca se
 * inventan filas aca. */
@Component
class NivelMembresiaPersistenceAdapter implements LoadNivelMembresiaPort {

    private final SpringDataNivelMembresiaRepository repository;

    NivelMembresiaPersistenceAdapter(SpringDataNivelMembresiaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<NivelMembresia> listar() {
        return repository.findAllByOrderByRangoAsc().stream()
                .map(e -> new NivelMembresia(e.getId(), e.getRango(), e.getNombre(), e.getPctProgresoMinimo()))
                .toList();
    }
}
