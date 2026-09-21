package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.CredencialesInvalidasException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.autenticacion.CanjeCodigoCommand;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.RegistroPendienteSocial;
import com.renaser.os.users.application.ports.out.autenticacion.TokenRegistroPendienteSocialPort;
import com.renaser.os.users.application.ports.out.autenticacion.VerificadorIdentidadProveedor;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.OrigenSocial;
import com.renaser.os.users.domain.model.identidadexterna.IdentidadExterna;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Login social (docs/MODULO_AUTH.md §6). Compone dos colaboradores: el adaptador del proveedor
 * (via {@link RegistroVerificadoresIdentidad}) y el puerto que resuelve {@code (proveedor,
 * sujeto)}. Desde D-65 (2026-09-01, §6.10) YA NO abre la {@code AccountRequest} directamente
 * cuando la identidad es nueva: la retiene en Redis (via {@link TokenRegistroPendienteSocialPort})
 * y devuelve un token de continuacion — abrir la solicitud es responsabilidad de
 * {@link CompletarRegistroSocialService}, el segundo paso.
 */
@Service
public class AutenticacionSocialService implements IniciarSesionConProveedorUseCase {

    /**
     * TTL del registro pendiente: igual al del OTP de alta ({@link VerificacionEmailService#VIGENCIA_CODIGO},
     * 10 minutos) — es el mismo orden de magnitud que le toma a una persona mirar el formulario
     * de confirmacion ya prellenado y mandarlo, y no deja la identidad verificada viva en Redis
     * mas tiempo del necesario.
     */
    static final Duration VIGENCIA_REGISTRO_PENDIENTE = VerificacionEmailService.VIGENCIA_CODIGO;

    /**
     * Limite por IP del login social (2026-09-21). La {@code requestIp} llegaba en el comando
     * desde que existe el endpoint y no la leia nadie: la cañeria estaba puesta y el limite no
     * existia, que es peor que no tenerlo, porque parece que lo hay.
     *
     * <p>Misma ventana que el resto del modulo. El tope queda por DEBAJO del login por contrasena
     * ({@code AutenticacionService.LIMITE_POR_IP}, 50) aunque para la persona sea el mismo gesto:
     * alli un intento cuesta un BCrypt y una lectura local, aca cuesta una peticion saliente
     * hacia Google/Apple/Facebook a nombre de la IP del producto. Queda por encima de los envios
     * de correo (20), que cuestan una cuota real de un tercero.
     *
     * <p><b>Asuncion, no confirmada por producto</b> (mismo criterio que A-5): tocar "Continuar
     * con Google" es un gesto ocasional; 30 por hora no le queda corto ni a varias personas
     * detras de un mismo NAT.
     */
    static final Duration VENTANA_RATE_LIMIT = Duration.ofHours(1);
    static final int LIMITE_POR_IP = 30;

    private final RegistroVerificadoresIdentidad verificadores;
    private final LoadIdentidadExternaPort loadIdentidadExternaPort;
    private final LoadAccountRequestPort loadAccountRequestPort;
    private final LoadUserPort loadUserPort;
    private final TokenRegistroPendienteSocialPort tokenRegistroPendienteSocialPort;
    private final LimitarSolicitudesResetPort limitarSolicitudesPort;

    public AutenticacionSocialService(List<VerificadorIdentidadProveedor> verificadores,
                                       LoadIdentidadExternaPort loadIdentidadExternaPort,
                                       LoadAccountRequestPort loadAccountRequestPort, LoadUserPort loadUserPort,
                                       TokenRegistroPendienteSocialPort tokenRegistroPendienteSocialPort,
                                       LimitarSolicitudesResetPort limitarSolicitudesPort) {
        this.verificadores = new RegistroVerificadoresIdentidad(verificadores);
        this.loadIdentidadExternaPort = loadIdentidadExternaPort;
        this.loadAccountRequestPort = loadAccountRequestPort;
        this.loadUserPort = loadUserPort;
        this.tokenRegistroPendienteSocialPort = tokenRegistroPendienteSocialPort;
        this.limitarSolicitudesPort = limitarSolicitudesPort;
    }

    /**
     * Cuatro caminos (D-65 mantiene el numero, cambia el tercero): el orden de los chequeos es
     * la parte importante, la identidad se resuelve SIEMPRE por {@code (proveedor, sujeto)} —
     * vinculo primero, solicitud previa despues —, y el correo se mira al final y solo para
     * explicar por que no se puede seguir, nunca para autenticar.
     */
    @Override
    public ResultadoLoginSocial iniciarSesion(IniciarSesionConProveedorCommand command) {
        // Antes de todo lo demas, porque lo primero que hace este metodo es salir de la maquina:
        // canjear el code contra el proveedor. Se cuenta TODO intento y no solo los que fallan,
        // por el mismo motivo que en el login por contrasena — el que acierta ya entro.
        rejectIfRateLimitExceeded(command.requestIp());
        VerificadorIdentidadProveedor verificador = verificadores.para(command.proveedor());
        IdentidadVerificada identidad = verificador.verificar(
                new CanjeCodigoCommand(command.code(), command.codeVerifier(), command.redirectUri()));
        identidad.exigirEmailVerificado(command.proveedor());
        OrigenSocial origen = new OrigenSocial(command.proveedor(), identidad.sujeto());

        Optional<IdentidadExterna> vinculo = loadIdentidadExternaPort.porProveedorYSujeto(origen.proveedor(),
                origen.sujetoProveedor());
        if (vinculo.isPresent()) {
            return new ResultadoLoginSocial.SesionIniciada(cargarUsuarioVinculado(vinculo.get().usuarioId()));
        }
        Optional<AccountRequest> solicitudPrevia = loadAccountRequestPort.porOrigenSocial(origen);
        if (solicitudPrevia.filter(solicitud -> solicitud.status().isPending()).isPresent()) {
            return new ResultadoLoginSocial.SolicitudEnRevision(solicitudPrevia.get().id());
        }
        if (loadUserPort.byEmail(new Email(identidad.email())).isPresent()) {
            return new ResultadoLoginSocial.CuentaExistenteSinVinculo(command.proveedor());
        }
        return retenerIdentidadPendiente(identidad, origen);
    }

    /**
     * El usuario detras del vinculo social, <b>y solo si su cuenta da acceso</b>.
     *
     * <p><b>El agujero que cierra (2026-09-18).</b> Este metodo hacia un {@code byId} y devolvia el
     * usuario sin mirar su estado, mientras que el login por contrasena si lo comprueba
     * ({@code AutenticacionService:76}, {@code credencial.cuentaHabilitada()}). Resultado: suspender
     * una cuenta con Google o Apple ya vinculado <b>no la suspendia</b> — la persona volvia a entrar
     * por {@code POST /api/v1/auth/social} y recuperaba una sesion valida. Revocar las sesiones al
     * suspender ({@code StaffAdminService}) no alcanzaba: podia abrir una nueva.
     *
     * <p>Pesa mas de lo que parece por como esta hecho el control de permisos: el interceptor
     * comprueba la suspension solo para TRAINEE y sale antes para MENTOR, ADMIN y ALCHEMIST, asi que
     * un mentor suspendido que volviera por aca seguia leyendo el expediente de sus aprendices.
     *
     * <p>Se lanza {@link CredencialesInvalidasException} y no una excepcion propia de "suspendido",
     * igual que en el login por contrasena: decirle al que llama que la cuenta existe pero esta
     * suspendida es enumeracion de usuarios, que el resto de este modulo cuida a proposito.
     */
    private User cargarUsuarioVinculado(UserId usuarioId) {
        User usuario = loadUserPort.byId(usuarioId)
                .orElseThrow(() -> new IllegalStateException(
                        "IdentidadExterna sin usuario correspondiente: " + usuarioId));
        if (!usuario.status().allowsAccess()) {
            throw new CredencialesInvalidasException();
        }
        return usuario;
    }

    /**
     * Identidad nueva: NO se abre la {@code AccountRequest} en esta misma llamada (D-65,
     * docs/MODULO_AUTH.md §6.10). Se retiene la identidad ya verificada con un token de un solo
     * uso, y la app tiene que mostrarle a la persona un formulario con {@code email}/{@code
     * fullName} ya prellenados antes de confirmar el alta.
     */
    private ResultadoLoginSocial retenerIdentidadPendiente(IdentidadVerificada identidad, OrigenSocial origen) {
        String fullName = nombreOFallback(identidad);
        String token = tokenRegistroPendienteSocialPort.generar(
                new RegistroPendienteSocial(origen.proveedor(), origen.sujetoProveedor(), identidad.email(),
                        fullName),
                VIGENCIA_REGISTRO_PENDIENTE);
        return new ResultadoLoginSocial.RegistroPendiente(token, identidad.email(), fullName);
    }

    /**
     * Sin IP no se cuenta, mismo criterio que el resto del modulo ({@code AutenticacionService},
     * {@code ResetContrasenaService}): un limite que no sabe a quien contarle no puede bloquear a
     * nadie sin bloquear a todos. En el camino HTTP real siempre viene — la pone el controller
     * con {@code DireccionIpDelCliente.de(...)}.
     */
    private void rejectIfRateLimitExceeded(String requestIp) {
        if (requestIp == null) {
            return;
        }
        if (!limitarSolicitudesPort.registrarIntento("social-login:ip:" + requestIp, VENTANA_RATE_LIMIT,
                LIMITE_POR_IP)) {
            throw new RateLimitExceededException("Demasiados intentos. Espera unos minutos.");
        }
    }

    private static String nombreOFallback(IdentidadVerificada identidad) {
        return identidad.nombre() != null && !identidad.nombre().isBlank() ? identidad.nombre() : identidad.email();
    }
}
