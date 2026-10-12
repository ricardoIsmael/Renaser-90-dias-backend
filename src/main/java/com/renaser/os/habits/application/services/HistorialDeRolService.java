package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.audiencia.AnotarCambioDeRolUseCase;
import com.renaser.os.habits.application.ports.out.audiencia.HistorialDeRolPort;
import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Spec 001 (D-264): el historial de rol que necesita la vigencia estricta; users no guarda historia de rol. */
@Service
class HistorialDeRolService implements AnotarCambioDeRolUseCase {

    private final HistorialDeRolPort historialDeRolPort;

    HistorialDeRolService(HistorialDeRolPort historialDeRolPort) {
        this.historialDeRolPort = historialDeRolPort;
    }

    @Override
    @Transactional
    public void anotar(UserId persona, String rolAnterior, String rolNuevo, Instant cuando) {
        historialDeRolPort.anotarCambio(persona, CodigoDeRol.of(rolAnterior), CodigoDeRol.of(rolNuevo), cuando);
    }
}
