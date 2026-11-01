package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.pagos;

import com.renaser.os.onboarding.application.ports.out.pagos.CuotasDePagoPort;
import com.renaser.os.onboarding.domain.model.pagos.Cuota;
import com.renaser.os.onboarding.domain.model.pagos.EstadoDeCuota;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Las cuotas en Postgres. Todo se pide siempre con el id de la persona: una cuota de otra no se puede leer, cambiar ni borrar por su id. {@code saveAndFlush} y los borrados
 * masivos corren dentro de la transacción del caso de uso.
 */
@Component
class CuotasDePagoPersistenceAdapter implements CuotasDePagoPort {

    private final SpringDataCuotaDePagoRepository repository;

    CuotasDePagoPersistenceAdapter(SpringDataCuotaDePagoRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Cuota> deLaPersona(UserId persona) {
        return repository.findByUsuarioIdOrderByVenceElAscCreadaEnAscIdAsc(persona.value()).stream().map(CuotasDePagoPersistenceAdapter::aDominio).toList();
    }

    @Override
    public void guardar(UserId persona, Cuota cuota) {
        repository.saveAndFlush(new CuotaDePagoJpaEntity(cuota.id(), persona.value(), cuota.venceEl(), cuota.monto(), cuota.estado().name(),
                cuota.estadoCambiadoPor() == null ? null : cuota.estadoCambiadoPor().value(), cuota.estadoCambiadoEn(), cuota.creadaEn()));
    }

    @Override
    public void eliminar(UserId persona, UUID cuota) {
        repository.eliminarDeLaPersona(persona.value(), cuota);
    }

    @Override
    public void eliminarTodas(UserId persona) {
        repository.eliminarTodasDeLaPersona(persona.value());
    }

    private static Cuota aDominio(CuotaDePagoJpaEntity e) {
        return new Cuota(e.getId(), e.getVenceEl(), e.getMonto(), EstadoDeCuota.valueOf(e.getEstado()),
                e.getEstadoCambiadoPor() == null ? null : UserId.of(e.getEstadoCambiadoPor()), e.getEstadoCambiadoEn(), e.getCreadaEn());
    }
}
