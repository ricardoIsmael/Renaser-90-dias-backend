package com.renaser.os.onboarding.infrastructure.adapter.out.cuentas;

import com.renaser.os.onboarding.api.FichaDeLaPersonaUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FichaAlAceptar;
import org.springframework.stereotype.Component;

/** Lo que {@code users} pide al aceptar una solicitud «con diagnóstico»: delega en el caso de uso de la ficha. */
@Component
class FichaAlAceptarAdapter implements FichaAlAceptar {

    private final FichaDeLaPersonaUseCase ficha;

    FichaAlAceptarAdapter(FichaDeLaPersonaUseCase ficha) {
        this.ficha = ficha;
    }

    @Override
    public void conDiagnostico(UserId persona, UserId quien) {
        ficha.asignarDiagnostico(persona, quien);
    }
}
