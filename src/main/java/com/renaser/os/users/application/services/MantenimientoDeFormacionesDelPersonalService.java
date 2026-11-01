package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.in.formacion.MantenerFormacionesDelPersonalUseCase;
import com.renaser.os.users.application.ports.out.formacion.CargarPertenenciaPort;
import com.renaser.os.users.application.ports.out.formacion.FormacionesDelPersonalPort;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ver {@link MantenerFormacionesDelPersonalUseCase}. Idempotente: repetir el aviso no cambia nada. */
@Service
class MantenimientoDeFormacionesDelPersonalService implements MantenerFormacionesDelPersonalUseCase {

    private final CargarPertenenciaPort pertenencias;
    private final FormacionesDelPersonalPort personal;

    MantenimientoDeFormacionesDelPersonalService(CargarPertenenciaPort pertenencias, FormacionesDelPersonalPort personal) {
        this.pertenencias = pertenencias;
        this.personal = personal;
    }

    @Override
    @Transactional
    public void alDarseDeAlta(UserId usuario) {
        pertenencias.de(usuario).ifPresent(p -> alCambiarDeRol(usuario, p.rol()));
    }

    @Override
    @Transactional
    public void alCambiarDeRol(UserId usuario, UserRole rolNuevo) {
        if (!ReglaDeAlcance.esPersonalConFormaciones(rolNuevo)) {
            personal.quitarTodas(usuario);
            return;
        }
        Formacion inicial = pertenencias.de(usuario).map(p -> p.deLaParticipacion()).orElse(null);
        personal.asignarSiNoTieneNinguna(usuario, inicial == null ? Formacion.POR_DEFECTO : inicial);
    }
}
