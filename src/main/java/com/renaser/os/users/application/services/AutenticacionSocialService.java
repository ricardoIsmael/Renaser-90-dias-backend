package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.IdentidadProveedorInvalidaException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.autenticacion.CanjeCodigoCommand;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.application.ports.out.autenticacion.LoadIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.VerificadorIdentidadProveedor;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.accountrequest.OrigenSocial;
import com.renaser.os.users.domain.model.identidadexterna.IdentidadExterna;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Login social (docs/MODULO_AUTH.md §6). Compone tres colaboradores: el adaptador del proveedor
 * (via {@link RegistroVerificadoresIdentidad}), el puerto que resuelve {@code (proveedor, sujeto)}
 * y, cuando la identidad es nueva, el mismo {@link SubmitAccountRequestUseCase} que ya usa el
 * autoregistro por formulario — el alta social NO es un camino paralelo, es el mismo camino con
 * el email pre-verificado por el proveedor en vez de por el usuario tipeandolo.
 */
@Service
public class AutenticacionSocialService implements IniciarSesionConProveedorUseCase {

    private final RegistroVerificadoresIdentidad verificadores;
    private final LoadIdentidadExternaPort loadIdentidadExternaPort;
    private final LoadAccountRequestPort loadAccountRequestPort;
    private final LoadUserPort loadUserPort;
    private final SubmitAccountRequestUseCase submitAccountRequestUseCase;
    private final TokenVerificacionEmailPort tokenVerificacionEmailPort;

    public AutenticacionSocialService(List<VerificadorIdentidadProveedor> verificadores,
                                       LoadIdentidadExternaPort loadIdentidadExternaPort,
                                       LoadAccountRequestPort loadAccountRequestPort, LoadUserPort loadUserPort,
                                       SubmitAccountRequestUseCase submitAccountRequestUseCase,
                                       TokenVerificacionEmailPort tokenVerificacionEmailPort) {
        this.verificadores = new RegistroVerificadoresIdentidad(verificadores);
        this.loadIdentidadExternaPort = loadIdentidadExternaPort;
        this.loadAccountRequestPort = loadAccountRequestPort;
        this.loadUserPort = loadUserPort;
        this.submitAccountRequestUseCase = submitAccountRequestUseCase;
        this.tokenVerificacionEmailPort = tokenVerificacionEmailPort;
    }

    /**
     * Cuatro caminos, no dos (2026-08-31, cierre de A-7): antes solo se distinguia "ya vinculado"
     * de "todo lo demas", y "todo lo demas" terminaba siempre en el mismo 409 generico. El orden
     * de los chequeos es la parte importante: la identidad se resuelve SIEMPRE por
     * {@code (proveedor, sujeto)} — vinculo primero, solicitud previa despues —, y el correo se
     * mira al final y solo para explicar por que no se puede seguir, nunca para autenticar.
     */
    @Override
    public ResultadoLoginSocial iniciarSesion(IniciarSesionConProveedorCommand command) {
        VerificadorIdentidadProveedor verificador = verificadores.para(command.proveedor());
        IdentidadVerificada identidad = verificador.verificar(
                new CanjeCodigoCommand(command.code(), command.codeVerifier(), command.redirectUri()));
        requireEmailVerificado(identidad, command.proveedor());
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
        return crearSolicitudDeAlta(command, identidad, origen);
    }

    private User cargarUsuarioVinculado(UserId usuarioId) {
        return loadUserPort.byId(usuarioId)
                .orElseThrow(() -> new IllegalStateException(
                        "IdentidadExterna sin usuario correspondiente: " + usuarioId));
    }

    /**
     * §6.4: "no se crea el usuario en silencio" — misma AccountRequest que el autoregistro.
     * {@link SubmitAccountRequestCommand} exige un {@code verificationToken} (2026-08-27,
     * ver {@code ConfirmarCodigoVerificacionEmailUseCase}) que normalmente sale de tipear un
     * codigo de 6 digitos — pero {@code requireEmailVerificado} ya confirmo que el PROVEEDOR
     * (Google/Apple) responde por este email, una garantia mas fuerte que nuestro propio
     * codigo. En vez de forzar al usuario a verificar dos veces el mismo correo, se genera el
     * token directo (mismo puerto, mismo mecanismo, se salta el paso del codigo).
     */
    private ResultadoLoginSocial crearSolicitudDeAlta(IniciarSesionConProveedorCommand command,
                                                        IdentidadVerificada identidad, OrigenSocial origen) {
        String phone = requirePhoneParaAlta(command.phone());
        String fullName = nombreOFallback(identidad);
        String verificationToken = tokenVerificacionEmailPort.generar(identidad.email(),
                VerificacionEmailService.VIGENCIA_TOKEN_VERIFICACION);
        // Sin contrasena (null): esta cuenta entra por el proveedor, no por clave propia. Es el
        // caso para el que `usuarios.hash_contrasena` ya era nullable. Si mas adelante quiere
        // una, la fija por "olvide mi contrasena" como cualquiera.
        // El (proveedor, sujeto) viaja DENTRO del comando de aplicacion, servidor a servidor: no
        // pasa por HTTP ni por el cliente en ningun momento. Es lo que permite que approve()
        // escriba la IdentidadExterna sin volver a pedirle nada a nadie (A-7).
        AccountRequestId solicitudId = submitAccountRequestUseCase.submit(
                SubmitAccountRequestCommand.porProveedorSocial(identidad.email(), fullName, phone,
                        command.city(), verificationToken, command.requestIp(),
                        origen.proveedor(), origen.sujetoProveedor()));
        return new ResultadoLoginSocial.SolicitudCreada(solicitudId);
    }

    private static String requirePhoneParaAlta(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException(
                    "Se requiere un telefono para completar el registro con este proveedor");
        }
        return phone;
    }

    private static String nombreOFallback(IdentidadVerificada identidad) {
        return identidad.nombre() != null && !identidad.nombre().isBlank() ? identidad.nombre() : identidad.email();
    }

    /**
     * Defensa adicional, no pedida explicitamente por el diseño pero consistente con su postura
     * de seguridad (§6.4): un {@code email_verified=false} significa que el proveedor no responde
     * por ese correo, y §6.4 se apoya en que "el email llega pre-verificado" para saltear la
     * verificacion propia — si el proveedor no lo confirma, esa premisa no se cumple.
     */
    private static void requireEmailVerificado(IdentidadVerificada identidad, ProveedorIdentidad proveedor) {
        if (!identidad.emailVerificado()) {
            throw new IdentidadProveedorInvalidaException(proveedor.name());
        }
    }
}
