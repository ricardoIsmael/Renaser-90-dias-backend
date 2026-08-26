package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionUseCase.IniciarSesionCommand;
import com.renaser.os.users.application.ports.in.user.GetMyProfileUseCase;
import com.renaser.os.users.domain.model.user.User;
import com.renaser.os.users.infrastructure.adapter.in.rest.user.UserResponse;
import com.renaser.os.users.infrastructure.adapter.in.web.security.SesionWebAdapter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sesion propia (D-49, docs/MODULO_AUTH.md): sin JWT, sin Supabase. El login establece una
 * sesion opaca via Spring Session sobre Redis; la cookie de sesion es la unica credencial que
 * viaja despues de este endpoint. {@link SesionWebAdapter} concentra todo el manejo de
 * transporte (SecurityContext/HttpSession) para que estos metodos queden en la forma exacta de
 * CLAUDE.MD §5.4.6: deserializar, invocar UN colaborador, mapear salida.
 *
 * <p>Todavia NO reemplaza a {@code X-Actor-Id} en el resto de la API (fase 4, pendiente) —
 * este controller prueba que el mecanismo de sesion funciona de punta a punta, no migra nada.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacionController {

    private final IniciarSesionUseCase iniciarSesionUseCase;
    private final GetMyProfileUseCase getMyProfileUseCase;
    private final SesionWebAdapter sesionWeb;

    public AutenticacionController(IniciarSesionUseCase iniciarSesionUseCase, GetMyProfileUseCase getMyProfileUseCase,
                                    SesionWebAdapter sesionWeb) {
        this.iniciarSesionUseCase = iniciarSesionUseCase;
        this.getMyProfileUseCase = getMyProfileUseCase;
        this.sesionWeb = sesionWeb;
    }

    @PostMapping("/login")
    public UserResponse login(@RequestBody @Valid LoginRequest request, HttpServletRequest servletRequest,
                               HttpServletResponse servletResponse) {
        User actor = iniciarSesionUseCase.iniciarSesion(new IniciarSesionCommand(request.email(),
                request.contrasena()));
        sesionWeb.establecer(actor.id(), servletRequest, servletResponse);
        return UserResponse.from(actor);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest servletRequest) {
        sesionWeb.cerrar(servletRequest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(getMyProfileUseCase.getMyProfile(sesionWeb.actorActual()));
    }
}
