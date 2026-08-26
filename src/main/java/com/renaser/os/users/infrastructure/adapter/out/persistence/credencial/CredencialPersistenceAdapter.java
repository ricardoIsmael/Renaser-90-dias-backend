package com.renaser.os.users.infrastructure.adapter.out.persistence.credencial;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.SaveCredencialPort;
import com.renaser.os.users.domain.model.user.Credencial;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
class CredencialPersistenceAdapter implements LoadCredencialPort, SaveCredencialPort {

    /** Valor del enum Postgres `estado_usuario`, no del enum Java: la BD esta en espanol. */
    private static final String ESTADO_HABILITADO = "ACTIVO";

    private final SpringDataCredencialRepository repository;

    CredencialPersistenceAdapter(SpringDataCredencialRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CredencialParaLogin> porEmail(String email) {
        return repository.buscarPorEmail(email).map(CredencialPersistenceAdapter::aDominio);
    }

    @Override
    @Transactional
    public void guardar(UserId usuarioId, Credencial credencial) {
        int filas = repository.actualizarHash(usuarioId.value(), credencial.hash(), credencial.actualizadaEn());
        if (filas == 0) {
            throw new IllegalStateException("No existe el usuario al que se le intenta fijar la contrasena");
        }
    }

    /** Fail-closed: cualquier estado que no sea ACTIVO niega el acceso, INACTIVO incluido (R-3). */
    private static CredencialParaLogin aDominio(SpringDataCredencialRepository.CredencialRow fila) {
        return new CredencialParaLogin(UserId.of(fila.getId()), fila.getHashContrasena(),
                ESTADO_HABILITADO.equals(fila.getEstado()));
    }
}
