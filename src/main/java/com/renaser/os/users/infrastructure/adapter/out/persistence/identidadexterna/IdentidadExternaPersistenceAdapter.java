package com.renaser.os.users.infrastructure.adapter.out.persistence.identidadexterna;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.autenticacion.LoadIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.SaveIdentidadExternaPort;
import com.renaser.os.users.domain.model.identidadexterna.IdentidadExterna;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
class IdentidadExternaPersistenceAdapter implements LoadIdentidadExternaPort, SaveIdentidadExternaPort {

    private final SpringDataIdentidadExternaRepository repository;

    IdentidadExternaPersistenceAdapter(SpringDataIdentidadExternaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<IdentidadExterna> porProveedorYSujeto(ProveedorIdentidad proveedor, String sujetoProveedor) {
        return repository.buscar(proveedor.name(), sujetoProveedor)
                .map(IdentidadExternaPersistenceAdapter::aDominio);
    }

    /**
     * Una PK duplicada ({@code DataIntegrityViolationException}, dos requests concurrentes
     * vinculando el mismo {@code (proveedor, sujeto)}) se deja subir tal cual: el
     * {@code GlobalExceptionHandler} ya la traduce a 409 — es exactamente el caso "perdio una
     * carrera" que ese handler documenta.
     */
    @Override
    @Transactional
    public void guardar(IdentidadExterna identidad) {
        repository.insertar(identidad.proveedor().name(), identidad.sujetoProveedor(),
                identidad.usuarioId().value(), identidad.emailProveedor(), identidad.vinculadaEn());
    }

    private static IdentidadExterna aDominio(SpringDataIdentidadExternaRepository.IdentidadExternaRow fila) {
        return IdentidadExterna.rehydrate(ProveedorIdentidad.valueOf(fila.getProveedor()), fila.getSujetoProveedor(),
                UserId.of(fila.getUsuarioId()), fila.getEmailProveedor(), fila.getVinculadaEn());
    }
}
