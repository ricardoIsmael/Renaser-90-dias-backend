package com.renaser.os.users.infrastructure.adapter.in.rest.autenticacion;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.web.security.PublicEndpoint;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.application.ports.in.autenticacion.CompletarRegistroSocialUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.CompletarRegistroSocialUseCase.CompletarRegistroSocialCommand;
import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.ConfirmarResetContrasenaUseCase.ConfirmarResetContrasenaCommand;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.IniciarSesionConProveedorCommand;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.ResultadoLoginSocial;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionUseCase.IniciarSesionCommand;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarResetContrasenaUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.SolicitarResetContrasenaUseCase.SolicitarResetContrasenaCommand;
import com.renaser.os.users.application.ports.in.autenticacion.VincularIdentidadSocialUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.VincularIdentidadSocialUseCase.VincularIdentidadSocialCommand;
import com.renaser.os.shared.web.ApiErrorResponse;
import com.renaser.os.users.application.ports.in.user.GetMyProfileUseCase;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
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
    private final VincularIdentidadSocialUseCase vincularIdentidadSocialUseCase;
    private final CompletarRegistroSocialUseCase completarRegistroSocialUseCase;

    public AutenticacionController(IniciarSesionUseCase iniciarSesionUseCase, GetMyProfileUseCase getMyProfileUseCase,
                                    SesionWebAdapter sesionWeb,
                                    SolicitarResetContrasenaUseCase solicitarResetContrasenaUseCase,
                                    ConfirmarResetContrasenaUseCase confirmarResetContrasenaUseCase,
                                    IniciarSesionConProveedorUseCase iniciarSesionConProveedorUseCase,
                                    VincularIdentidadSocialUseCase vincularIdentidadSocialUseCase,
                                    CompletarRegistroSocialUseCase completarRegistroSocialUseCase) {
        this.iniciarSesionUseCase = iniciarSesionUseCase;
        this.getMyProfileUseCase = getMyProfileUseCase;
        this.sesionWeb = sesionWeb;
        this.solicitarResetContrasenaUseCase = solicitarResetContrasenaUseCase;
        this.confirmarResetContrasenaUseCase = confirmarResetContrasenaUseCase;
        this.iniciarSesionConProveedorUseCase = iniciarSesionConProveedorUseCase;
        this.vincularIdentidadSocialUseCase = vincularIdentidadSocialUseCase;
        this.completarRegistroSocialUseCase = completarRegistroSocialUseCase;
    }

    @PublicEndpoint("Es el login: pedir una sesion para poder iniciar sesion no cierra.")
    @PostMapping("/login")
    public UserResponse login(@RequestBody @Valid LoginRequest request, HttpServletRequest servletRequest,
                               HttpServletResponse servletResponse) {
        User actor = iniciarSesionUseCase.iniciarSesion(new IniciarSesionCommand(request.email(),
                request.contrasena()));
        sesionWeb.establecer(actor.id(), servletRequest, servletResponse);
        return UserResponse.from(actor);
    }

    @PublicEndpoint("Cerrar una sesion que no existe es idempotente; exigir cuenta solo agregaria un 403 sin efecto.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest servletRequest) {
        sesionWeb.cerrar(servletRequest);
        return ResponseEntity.noContent().build();
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "exige sesion real: no acepta el respaldo de X-Actor-Id (el otro que tampoco lo acepta es POST /social/link)")
    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(getMyProfileUseCase.getMyProfile(sesionWeb.actorActual()));
    }

    /**
     * Responde 202 SIEMPRE, exista o no una cuenta con ese email — {@link SolicitarResetContrasenaUseCase}
     * ya garantiza que el comportamiento observable es identico en ambos casos (no-enumeracion).
     */
    @PublicEndpoint("Se pide justamente cuando no se puede iniciar sesion. Responde 202 siempre, para no revelar si el correo existe.")
    @PostMapping("/password/reset-request")
    public ResponseEntity<Void> solicitarResetContrasena(@RequestBody @Valid SolicitarResetContrasenaRequest request,
                                                           HttpServletRequest servletRequest) {
        solicitarResetContrasenaUseCase.solicitar(
                new SolicitarResetContrasenaCommand(request.email(), servletRequest.getRemoteAddr()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PublicEndpoint("La credencial es el token de reset, no la sesion: quien lo usa todavia no puede entrar.")
    @PostMapping("/password/reset-confirm")
    public ResponseEntity<Void> confirmarResetContrasena(@RequestBody @Valid ConfirmarResetContrasenaRequest request) {
        confirmarResetContrasenaUseCase.confirmar(
                new ConfirmarResetContrasenaCommand(request.token(), request.contrasenaNueva()));
        return ResponseEntity.noContent().build();
    }

    /**
     * Login social (docs/MODULO_AUTH.md §6.1, §6.10). {@code ResultadoLoginSocial} es sellado a
     * proposito: el switch de abajo es el unico lugar de todo el flujo que decide si corresponde
     * establecer sesion, y el compilador obliga a cubrir los cuatro caminos. El controller sigue
     * siendo tonto (CLAUDE.MD §5.4.6): no decide nada de negocio, solo traduce una variante ya
     * decidida por el caso de uso a un codigo HTTP.
     *
     * <p>Los cuatro (2026-09-01, D-65 — el tercero cambio de forma, los otros tres no):
     *
     * <ul>
     *   <li><b>200</b> sesion establecida + perfil.</li>
     *   <li><b>202</b> identidad nueva: {@code RegistroPendienteSocialResponse} con el token de
     *       continuacion y los datos para prellenar el formulario. TODAVIA no existe ninguna
     *       {@code AccountRequest} — se crea recien en {@code POST /auth/social/complete}. Antes
     *       de D-65 este caso creaba la solicitud en esta misma llamada ({@code estado:
     *       "CREADA"}); esa variante dejo de producirse porque el {@code code} de OAuth es de un
     *       solo uso y para cuando el backend conocia el correo/nombre, ya habia decidido que
     *       hacer con ellos, sin darle a la app la chance de mostrar un formulario.</li>
     *   <li><b>202</b> solicitud previa todavia pendiente — {@code estado: "EN_REVISION"}.
     *       <b>No es un error:</b> la persona ya se registro y espera aprobacion, y la app tiene
     *       que poder mostrarle eso en vez de un fallo.</li>
     *   <li><b>409</b> el correo ya tiene cuenta pero esta identidad social no esta vinculada a
     *       ella. Vincular por coincidencia de correo es como se apodera alguien de una cuenta
     *       ajena (§6.4), asi que no se vincula: se rechaza y la persona entra con su
     *       contrasena.</li>
     * </ul>
     */
    @PublicEndpoint("Es el login por Google/Apple/Facebook: la credencial es el token del proveedor.")
    @PostMapping("/social")
    public ResponseEntity<?> loginSocial(@RequestBody @Valid LoginSocialRequest request,
                                          HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        ResultadoLoginSocial resultado = iniciarSesionConProveedorUseCase.iniciarSesion(
                new IniciarSesionConProveedorCommand(request.proveedor(), request.code(), request.codeVerifier(),
                        request.redirectUri(), servletRequest.getRemoteAddr()));
        return switch (resultado) {
            case ResultadoLoginSocial.SesionIniciada sesion -> {
                sesionWeb.establecer(sesion.usuario().id(), servletRequest, servletResponse);
                yield ResponseEntity.ok(UserResponse.from(sesion.usuario()));
            }
            case ResultadoLoginSocial.RegistroPendiente pendiente -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(RegistroPendienteSocialResponse.from(pendiente));
            case ResultadoLoginSocial.SolicitudEnRevision solicitud -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(SolicitudSocialResponse.enRevision(solicitud));
            case ResultadoLoginSocial.CuentaExistenteSinVinculo cuenta -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiErrorResponse.of("Ya existe una cuenta con este correo y no esta vinculada a "
                            + cuenta.proveedor() + ". Inicia sesion con tu contrasena para entrar. "
                            + "Una vez adentro, podes vincular " + cuenta.proveedor()
                            + " a tu cuenta desde tu perfil."));
        };
    }

    /**
     * Segundo paso del alta social (docs/MODULO_AUTH.md §6.10, D-65, 2026-09-01): confirma el
     * formulario que la app prellena con lo que devolvio {@code POST /auth/social} cuando la
     * identidad era nueva, y recien ACA se abre la {@code AccountRequest}.
     *
     * <p><b>PUBLICO a proposito:</b> quien lo llama todavia no tiene cuenta — la credencial es
     * poseer el {@code registroPendienteToken}, no una sesion. El correo NUNCA viaja en este
     * request: sale del registro que guardo Redis, nunca del cuerpo (mismo blindaje que el
     * {@code role} ausente del alta publica, CLAUDE.MD §5.3.3).
     *
     * <ul>
     *   <li><b>202</b> {@code AccountRequestIdResponse}, igual que el alta por formulario.</li>
     *   <li><b>400</b> token invalido, vencido, o ya usado — hay que rehacer el flujo del
     *       proveedor social desde el principio.</li>
     * </ul>
     */
    @PublicEndpoint("Segundo paso del alta social: quien llama todavia no tiene cuenta, la credencial es el "
            + "token de continuacion que devolvio /auth/social.")
    @PostMapping("/social/complete")
    public ResponseEntity<AccountRequestIdResponse> completarRegistroSocial(
            @RequestBody @Valid CompletarRegistroSocialRequest request, HttpServletRequest servletRequest) {
        AccountRequestId solicitudId = completarRegistroSocialUseCase.completar(new CompletarRegistroSocialCommand(
                request.registroPendienteToken(), request.fullName(), request.phone(), request.city(),
                servletRequest.getRemoteAddr()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new AccountRequestIdResponse(solicitudId.value()));
    }

    /**
     * Vincular una identidad social a la cuenta que ya inicio sesion
     * (docs/MODULO_AUTH.md §6.9). Es la salida del 409 de arriba: hasta ahora ese mensaje
     * mandaba a la persona a entrar con su contrasena y despues no habia forma de conectar su
     * Google.
     *
     * <p><b>Exige sesion real, igual que {@code GET /me} y por un motivo mas fuerte:</b> si este
     * endpoint aceptara el respaldo de {@code X-Actor-Id} — un header que cualquiera escribe —
     * cualquiera podria colgar su cuenta de Google del usuario de otro y entrar como el para
     * siempre. Seria exactamente el agujero que este endpoint viene a cerrar.
     *
     * <ul>
     *   <li><b>204</b> vinculada. Tambien si ya estaba vinculada a ESTA misma cuenta: el caso de
     *       uso es idempotente, el doble tap del cliente movil no es un error.</li>
     *   <li><b>409</b> esa identidad ya pertenece a otro usuario (
     *       {@code IdentidadYaVinculadaException}).</li>
     *   <li><b>401</b> sin sesion ({@code SesionNoIniciadaException}), o el proveedor rechazo el
     *       {@code code} ({@code IdentidadProveedorInvalidaException}) — los dos ya mapeados en
     *       {@code GlobalExceptionHandler}.</li>
     *   <li><b>403</b> la cuenta esta suspendida.</li>
     * </ul>
     */
    @RequiresPermission(value = Permission.USE_APP,
            scope = "exige sesion real, NO acepta el respaldo de X-Actor-Id: vincular una identidad "
                    + "con un header que cualquiera escribe seria apropiacion de cuenta")
    @PostMapping("/social/link")
    public ResponseEntity<Void> vincularIdentidadSocial(@RequestBody @Valid VincularIdentidadSocialRequest request) {
        vincularIdentidadSocialUseCase.vincular(new VincularIdentidadSocialCommand(sesionWeb.actorActual(),
                request.proveedor(), request.code(), request.codeVerifier(), request.redirectUri()));
        return ResponseEntity.noContent().build();
    }
}
