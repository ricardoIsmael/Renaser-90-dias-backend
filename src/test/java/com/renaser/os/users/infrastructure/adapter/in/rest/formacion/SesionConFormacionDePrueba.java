package com.renaser.os.users.infrastructure.adapter.in.rest.formacion;

import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase.FaseDelPrograma;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase.FormacionDeLaSesion;
import com.renaser.os.users.application.ports.in.formacion.ConsultarFormacionDeLaSesionUseCase.OpcionDeFormacion;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Para las pruebas de controller que arman una {@code UserResponse} (login, {@code /auth/me}, {@code /users/me}) y no hablan de formaciones
 * (spec 022): un {@link ArmadorDeLaSesion} real sobre una sesión fija de la Escuela, sin base de datos. Se suma con
 * {@code @Import(SesionConFormacionDePrueba.class)}.
 */
@TestConfiguration
public class SesionConFormacionDePrueba {

    public static FormacionDeLaSesion escuela() {
        OpcionDeFormacion escuela = new OpcionDeFormacion("ESCUELA", "Escuela Renaser");
        return new FormacionDeLaSesion(escuela, List.of(escuela), false, false, false, null, 90,
                List.of(new FaseDelPrograma(1, 1, 7), new FaseDelPrograma(2, 8, 34), new FaseDelPrograma(3, 35, 64),
                        new FaseDelPrograma(4, 65, 90)));
    }

    @Bean
    ArmadorDeLaSesion armadorDeLaSesion() {
        return new ArmadorDeLaSesion(persona -> escuela());
    }
}
