package com.renaser.os.users.infrastructure.adapter.in.rest.formacion;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase;
import com.renaser.os.users.infrastructure.adapter.in.rest.user.UserResponse;
import org.springframework.stereotype.Component;

/**
 * Suma la formación a la respuesta de la sesión (login, {@code /auth/me}, {@code /users/me}; spec 022, FR-012). Existe para que el controller de
 * autenticación invoque UN colaborador y no orqueste dos casos de uso.
 */
@Component
public class ArmadorDeLaSesion {

    private final ConsultarFormacionDeLaSesionUseCase consultar;

    public ArmadorDeLaSesion(ConsultarFormacionDeLaSesionUseCase consultar) {
        this.consultar = consultar;
    }

    public UserResponse conFormacion(UserResponse respuesta, UserId usuario) {
        return respuesta.conFormacion(FormacionDeLaSesionResponse.from(consultar.consultar(usuario)));
    }
}
