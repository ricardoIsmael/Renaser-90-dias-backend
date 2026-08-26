package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarResetContrasenaUseCase.ConfirmarResetContrasenaCommand;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.IniciarSesionConProveedorCommand;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.ResultadoLoginSocial;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionUseCase.IniciarSesionCommand;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarResetContrasenaUseCase.SolicitarResetContrasenaCommand;
import com.renaser.os.users.application.ports.in.user.GetMyProfileUseCase;
import com.renaser.os.users.domain.model.user.User;
import com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest.AccountRequestIdResponse;
import com.renaser.os.users.infrastructure.adapter.in.rest.user.UserResponse;
import com.renaser.os.users.infrastructure.adapter.in.web.security.SesionWebAdapter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    private final SolicitarResetContrasenaUseCase solicitarResetContrasenaUseCase;
    private final ConfirmarResetContrasenaUseCase confirmarResetContrasenaUseCase;
    private final IniciarSesionConProveedorUseCase iniciarSesionConProveedorUseCase;

    public AutenticacionController(IniciarSesionUseCase iniciarSesionUseCase, GetMyProfileUseCase getMyProfileUseCase,
                                    SesionWebAdapter sesionWeb,
                                    SolicitarResetContrasenaUseCase solicitarResetContrasenaUseCase,
                                    ConfirmarResetContrasenaUseCase confirmarResetContrasenaUseCase,
                                    IniciarSesionConProveedorUseCase iniciarSesionConProveedorUseCase) {
        this.iniciarSesionUseCase = iniciarSesionUseCase;
        this.getMyProfileUseCase = getMyProfileUseCase;
        this.sesionWeb = sesionWeb;
        this.solicitarResetContrasenaUseCase = solicitarResetContrasenaUseCase;
        this.confirmarResetContrasenaUseCase = confirmarResetContrasenaUseCase;
        this.iniciarSesionConProveedorUseCase = iniciarSesionConProveedorUseCase;
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

    /**
     * Responde 202 SIEMPRE, exista o no una cuenta con ese email — {@link SolicitarResetContrasenaUseCase}
     * ya garantiza que el comportamiento observable es identico en ambos casos (no-enumeracion).
     */
    @PostMapping("/password/reset-request")
    public ResponseEntity<Void> solicitarResetContrasena(@RequestBody @Valid SolicitarResetContrasenaRequest request,
                                                           HttpServletRequest servletRequest) {
        solicitarResetContrasenaUseCase.solicitar(
                new SolicitarResetContrasenaCommand(request.email(), servletRequest.getRemoteAddr()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/password/reset-confirm")
    public ResponseEntity<Void> confirmarResetContrasena(@RequestBody @Valid ConfirmarResetContrasenaRequest request) {
        confirmarResetContrasenaUseCase.confirmar(
                new ConfirmarResetContrasenaCommand(request.token(), request.contrasenaNueva()));
        return ResponseEntity.noContent().build();
    }

    /**
     * Login social (docs/MODULO_AUTH.md §6.1). {@code ResultadoLoginSocial} es sellado (dos
     * variantes) a proposito: el switch de abajo es el unico lugar de todo el flujo que decide si
     * corresponde establecer sesion, y el compilador obliga a cubrir el otro camino (identidad
     * nueva → sin sesion, 202 con la solicitud de alta recien creada).
     */
    @PostMapping("/social")
    public ResponseEntity<?> loginSocial(@RequestBody @Valid LoginSocialRequest request,
                                          HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        ResultadoLoginSocial resultado = iniciarSesionConProveedorUseCase.iniciarSesion(
                new IniciarSesionConProveedorCommand(request.proveedor(), request.code(), request.codeVerifier(),
                        request.redirectUri(), request.phone(), request.city(), servletRequest.getRemoteAddr()));
        return switch (resultado) {
            case ResultadoLoginSocial.SesionIniciada sesion -> {
                sesionWeb.establecer(sesion.usuario().id(), servletRequest, servletResponse);
                yield ResponseEntity.ok(UserResponse.from(sesion.usuario()));
            }
            case ResultadoLoginSocial.SolicitudCreada solicitud -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new AccountRequestIdResponse(solicitud.solicitudId().value()));
        };
    }
}
