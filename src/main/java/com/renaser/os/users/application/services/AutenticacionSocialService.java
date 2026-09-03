package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.autenticacion.CanjeCodigoCommand;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
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

    private final RegistroVerificadoresIdentidad verificadores;
    private final LoadIdentidadExternaPort loadIdentidadExternaPort;
    private final LoadAccountRequestPort loadAccountRequestPort;
    private final LoadUserPort loadUserPort;
    private final TokenRegistroPendienteSocialPort tokenRegistroPendienteSocialPort;

    public AutenticacionSocialService(List<VerificadorIdentidadProveedor> verificadores,
                                       LoadIdentidadExternaPort loadIdentidadExternaPort,
                                       LoadAccountRequestPort loadAccountRequestPort, LoadUserPort loadUserPort,
                                       TokenRegistroPendienteSocialPort tokenRegistroPendienteSocialPort) {
        this.verificadores = new RegistroVerificadoresIdentidad(verificadores);
        this.loadIdentidadExternaPort = loadIdentidadExternaPort;
        this.loadAccountRequestPort = loadAccountRequestPort;
        this.loadUserPort = loadUserPort;
        this.tokenRegistroPendienteSocialPort = tokenRegistroPendienteSocialPort;
    }

    /**
     * Cuatro caminos (D-65 mantiene el numero, cambia el tercero): el orden de los chequeos es
     * la parte importante, la identidad se resuelve SIEMPRE por {@code (proveedor, sujeto)} —
     * vinculo primero, solicitud previa despues —, y el correo se mira al final y solo para
     * explicar por que no se puede seguir, nunca para autenticar.
     */
    @Override
    public ResultadoLoginSocial iniciarSesion(IniciarSesionConProveedorCommand command) {
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

    private User cargarUsuarioVinculado(UserId usuarioId) {
        return loadUserPort.byId(usuarioId)
                .orElseThrow(() -> new IllegalStateException(
                        "IdentidadExterna sin usuario correspondiente: " + usuarioId));
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

    private static String nombreOFallback(IdentidadVerificada identidad) {
        return identidad.nombre() != null && !identidad.nombre().isBlank() ? identidad.nombre() : identidad.email();
    }
}
