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
import com.renaser.os.shared.web.ApiErrorResponse;
import com.renaser.os.users.application.ports.in.user.GetMyProfileUseCase;
import com.renaser.os.users.domain.model.user.User;
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
 * <p>Este controller prueba que el mecanismo de sesion funciona de punta a punta. El resto de
 * la API ya no lee {@code X-Actor-Id} directo: usa {@code @ActorAutenticado}, que resuelve el
 * actor contra esta misma sesion y solo cae al header como respaldo mientras dure la fase 4
 * de la migracion (docs/MODULO_AUTH.md §8).
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
     * Login social (docs/MODULO_AUTH.md §6.1). {@code ResultadoLoginSocial} es sellado a
     * proposito: el switch de abajo es el unico lugar de todo el flujo que decide si corresponde
     * establecer sesion, y el compilador obliga a cubrir los cuatro caminos. El controller sigue
     * siendo tonto (CLAUDE.MD §5.4.6): no decide nada de negocio, solo traduce una variante ya
     * decidida por el caso de uso a un codigo HTTP.
     *
     * <p>Los cuatro (2026-08-31, cierre de A-7 — antes eran dos y todo lo que no fuera sesion
     * terminaba en el mismo 409 generico):
     *
     * <ul>
     *   <li><b>200</b> sesion establecida + perfil.</li>
     *   <li><b>202</b> solicitud recien creada — {@code estado: "CREADA"}.</li>
     *   <li><b>202</b> solicitud previa todavia pendiente — {@code estado: "EN_REVISION"}.
     *       <b>No es un error:</b> la persona ya se registro y espera aprobacion, y la app tiene
     *       que poder mostrarle eso en vez de un fallo.</li>
     *   <li><b>409</b> el correo ya tiene cuenta pero esta identidad social no esta vinculada a
     *       ella. Vincular por coincidencia de correo es como se apodera alguien de una cuenta
     *       ajena (§6.4), asi que no se vincula: se rechaza y la persona entra con su
     *       contrasena. Es el mismo 409 que ya devolvia este endpoint para este caso, asi que el
     *       contrato no cambia.</li>
     * </ul>
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
                    .body(SolicitudSocialResponse.creada(solicitud));
            case ResultadoLoginSocial.SolicitudEnRevision solicitud -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(SolicitudSocialResponse.enRevision(solicitud));
            case ResultadoLoginSocial.CuentaExistenteSinVinculo cuenta -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiErrorResponse.of("Ya existe una cuenta con este correo y no esta vinculada a "
                            + cuenta.proveedor() + ". Inicia sesion con tu contrasena para entrar."));
        };
    }
}
