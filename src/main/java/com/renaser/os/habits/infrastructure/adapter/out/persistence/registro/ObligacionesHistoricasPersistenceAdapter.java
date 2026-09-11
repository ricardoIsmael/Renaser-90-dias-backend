package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.habits.api.EstadoObligacion;
import com.renaser.os.habits.infrastructure.adapter.out.persistence.habito.ExigenciaEvidenciaJpa;
import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.habits.application.ports.out.registro.ConsultarObligacionesHistoricasPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Component
class ObligacionesHistoricasPersistenceAdapter implements ConsultarObligacionesHistoricasPort {

    private final SpringDataRegistroHabitoRepository repository;

    ObligacionesHistoricasPersistenceAdapter(SpringDataRegistroHabitoRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ObligacionHabito> entre(Collection<UserId> participantes, LocalDate desde, LocalDate hasta) {
        List<UUID> ids = participantes.stream().map(UserId::value).toList();
        return repository.obligacionesEntre(ids, desde, hasta).stream().map(this::aObligacion).toList();
    }

    /**
     * Columnas en el orden de la proyeccion. Se mapea a mano y no con una interfaz de
     * proyeccion para que el enum publico {@link EstadoObligacion} quede traducido acá, en el
     * borde: si mañana la base gana un estado nuevo, el compilador obliga a decidir qué
     * significa en vez de dejarlo pasar.
     */
    private ObligacionHabito aObligacion(Object[] fila) {
        return new ObligacionHabito(
                (UUID) fila[0],
                UserId.of((UUID) fila[1]),
                (LocalDate) fila[2],
                ((Number) fila[3]).intValue(),
                (String) fila[4],
                traducir((EstadoRegistroJpa) fila[5]),
                fila[6] == ExigenciaEvidenciaJpa.OBLIGATORIA,
                Boolean.TRUE.equals(fila[7]));
    }

    private static EstadoObligacion traducir(EstadoRegistroJpa estado) {
        return switch (estado) {
            case PENDIENTE -> EstadoObligacion.PENDIENTE;
            case EN_CURSO -> EstadoObligacion.EN_CURSO;
            case COMPLETADO -> EstadoObligacion.COMPLETADO;
            case FALLIDO -> EstadoObligacion.FALLIDO;
            case EXPIRADO -> EstadoObligacion.EXPIRADO;
        };
    }
}
