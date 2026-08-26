package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.IdentidadProveedorInvalidaException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase;
import com.renaser.os.users.application.ports.out.autenticacion.CanjeCodigoCommand;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.application.ports.out.autenticacion.LoadIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.VerificadorIdentidadProveedor;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.identidadexterna.IdentidadExterna;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private final LoadUserPort loadUserPort;
    private final SubmitAccountRequestUseCase submitAccountRequestUseCase;

    public AutenticacionSocialService(List<VerificadorIdentidadProveedor> verificadores,
                                       LoadIdentidadExternaPort loadIdentidadExternaPort, LoadUserPort loadUserPort,
                                       SubmitAccountRequestUseCase submitAccountRequestUseCase) {
        this.verificadores = new RegistroVerificadoresIdentidad(verificadores);
        this.loadIdentidadExternaPort = loadIdentidadExternaPort;
        this.loadUserPort = loadUserPort;
        this.submitAccountRequestUseCase = submitAccountRequestUseCase;
    }

    @Override
    public ResultadoLoginSocial iniciarSesion(IniciarSesionConProveedorCommand command) {
        VerificadorIdentidadProveedor verificador = verificadores.para(command.proveedor());
        IdentidadVerificada identidad = verificador.verificar(
                new CanjeCodigoCommand(command.code(), command.codeVerifier(), command.redirectUri()));
        requireEmailVerificado(identidad, command.proveedor());

        Optional<IdentidadExterna> vinculo = loadIdentidadExternaPort.porProveedorYSujeto(command.proveedor(),
                identidad.sujeto());
        if (vinculo.isPresent()) {
            return new ResultadoLoginSocial.SesionIniciada(cargarUsuarioVinculado(vinculo.get().usuarioId()));
        }
        return crearSolicitudDeAlta(command, identidad);
    }

    private User cargarUsuarioVinculado(UserId usuarioId) {
        return loadUserPort.byId(usuarioId)
                .orElseThrow(() -> new IllegalStateException(
                        "IdentidadExterna sin usuario correspondiente: " + usuarioId));
    }

    /**
     * §6.4: "no se crea el usuario en silencio" — misma AccountRequest que el autoregistro. El
     * `supabaseUserId` que exige {@link SubmitAccountRequestCommand} es, en los hechos, el id que
     * tendra el usuario si se aprueba (no un id real de Supabase Auth, que ya no existe — D-49);
     * se pre-genera aca por el mismo motivo que el autoregistro lo trae del cliente.
     */
    private ResultadoLoginSocial crearSolicitudDeAlta(IniciarSesionConProveedorCommand command,
                                                        IdentidadVerificada identidad) {
        rejectIfEmailYaRegistrado(identidad.email());
        String phone = requirePhoneParaAlta(command.phone());
        String fullName = nombreOFallback(identidad);
        AccountRequestId solicitudId = submitAccountRequestUseCase.submit(new SubmitAccountRequestCommand(
                UUID.randomUUID().toString(), identidad.email(), fullName, phone, command.city(),
                command.requestIp()));
        return new ResultadoLoginSocial.SolicitudCreada(solicitudId);
    }

    /**
     * §6.4: un email que coincide con un usuario ya existente NO vincula automaticamente — eso
     * requeriria que el dueño de la cuenta lo confirme estando ya autenticado, una funcionalidad
     * que todavia no existe (ver docs/MODULO_AUTH.md §10). Mientras tanto, se rechaza en vez de
     * crear una AccountRequest duplicada para un email que ya tiene cuenta.
     */
    private void rejectIfEmailYaRegistrado(String email) {
        if (loadUserPort.byEmail(new Email(email)).isPresent()) {
            throw new IllegalStateException("Ya existe una cuenta con este email. Inicia sesion con tu metodo "
                    + "actual; vincular una cuenta social a un usuario existente todavia no esta disponible.");
        }
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
