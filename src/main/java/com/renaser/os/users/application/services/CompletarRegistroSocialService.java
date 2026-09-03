package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.RegistroPendienteSocialInvalidoException;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand;
import com.renaser.os.users.application.ports.in.autenticacion.CompletarRegistroSocialUseCase;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.autenticacion.RegistroPendienteSocial;
import com.renaser.os.users.application.ports.out.autenticacion.TokenRegistroPendienteSocialPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequest;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.accountrequest.OrigenSocial;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Segundo paso del alta social (docs/MODULO_AUTH.md §6.10, D-65). Clase propia y no un metodo
 * mas de {@link AutenticacionSocialService} (CLAUDE.MD §5.4.8, una clase por caso de uso):
 * aquel verifica una identidad y decide que camino sigue; este SOLO sabe convertir un registro
 * pendiente ya verificado en una {@code AccountRequest}.
 */
@Service
public class CompletarRegistroSocialService implements CompletarRegistroSocialUseCase {

    private static final Logger log = LoggerFactory.getLogger(CompletarRegistroSocialService.class);

    private final TokenRegistroPendienteSocialPort tokenRegistroPendienteSocialPort;
    private final SubmitAccountRequestUseCase submitAccountRequestUseCase;
    private final TokenVerificacionEmailPort tokenVerificacionEmailPort;
    private final LoadAccountRequestPort loadAccountRequestPort;

    public CompletarRegistroSocialService(TokenRegistroPendienteSocialPort tokenRegistroPendienteSocialPort,
                                           SubmitAccountRequestUseCase submitAccountRequestUseCase,
                                           TokenVerificacionEmailPort tokenVerificacionEmailPort,
                                           LoadAccountRequestPort loadAccountRequestPort) {
        this.tokenRegistroPendienteSocialPort = tokenRegistroPendienteSocialPort;
        this.submitAccountRequestUseCase = submitAccountRequestUseCase;
        this.tokenVerificacionEmailPort = tokenVerificacionEmailPort;
        this.loadAccountRequestPort = loadAccountRequestPort;
    }

    /**
     * El correo y el sujeto del proveedor salen SIEMPRE de {@code registro} — nunca del
     * comando (docs/MODULO_AUTH.md §6.10): es la regla de seguridad central de este paso. Si el
     * cliente pudiera mandar el correo aca, cualquiera completaria un registro con el correo de
     * otra persona.
     */
    @Override
    public AccountRequestId completar(CompletarRegistroSocialCommand command) {
        RegistroPendienteSocial registro = tokenRegistroPendienteSocialPort
                .consumir(command.registroPendienteToken())
                .orElseThrow(RegistroPendienteSocialInvalidoException::new);

        // Mismo camino que ya usaba AutenticacionSocialService antes de D-65 (docs/MODULO_AUTH.md
        // §6.7): el proveedor ya confirmo la propiedad del correo (mas fuerte que nuestro propio
        // codigo de 6 digitos), asi que se genera el token de verificacion directo en vez de
        // pedirle a la persona que lo verifique otra vez.
        String verificationToken = tokenVerificacionEmailPort.generar(registro.email(),
                VerificacionEmailService.VIGENCIA_TOKEN_VERIFICACION);

        try {
            return submitAccountRequestUseCase.submit(SubmitAccountRequestCommand.porProveedorSocial(
                    registro.email(), command.fullName(), command.phone(), command.city(), verificationToken,
                    command.requestIp(), registro.proveedor(), registro.sujetoProveedor()));
        } catch (DataIntegrityViolationException carreraDeAltaConcurrente) {
            return solicitudYaCreadaPorOtraLlamada(registro, carreraDeAltaConcurrente);
        }
    }

    /**
     * C-17 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html): dos pestañas, un
     * reintento de red del celular, o un doble tap sobre "confirmar" pueden generar DOS tokens
     * de registro pendiente independientes para la MISMA identidad social
     * ({@link AutenticacionSocialService#retenerIdentidadPendiente} arma uno nuevo en cada login,
     * sin acordarse del anterior) y las dos confirmaciones llegar casi juntas a este metodo. La
     * primera crea el {@code User} y la {@code AccountRequest} y hace commit; la segunda pierde
     * la carrera contra el UNIQUE de {@code usuarios.email}/{@code solicitudes_cuenta.email} — sin
     * este catch, esa segunda llamada terminaba en el 409 generico de
     * {@code GlobalExceptionHandler#handleIntegridad} ("la operacion entra en conflicto con datos
     * que ya existen") en vez del mismo "tu solicitud ya esta en revision" que ya recibe quien
     * toca "Continuar con Google" una TERCERA vez ({@code SolicitudEnRevision} en
     * {@code AutenticacionSocialService}). Mismo criterio que ya aplica
     * {@code vincularIdentidadSocial} en este modulo: "el doble tap del cliente movil no es un
     * error".
     *
     * <p>Solo se recupera la carrera si la solicitud que gano es, efectivamente, de esta MISMA
     * identidad social ({@code porOrigenSocial}, nunca por correo — docs/MODULO_AUTH.md §6.4): si
     * el conflicto de UNIQUE vino de otro lado (alguien mando un alta por formulario con el mismo
     * correo casi al mismo tiempo), no hay nada que recuperar y se relanza la excepcion original
     * para que el cliente siga viendo el 409 generico — mejor eso que devolverle el id de una
     * solicitud ajena.
     */
    private AccountRequestId solicitudYaCreadaPorOtraLlamada(RegistroPendienteSocial registro,
                                                              DataIntegrityViolationException original) {
        OrigenSocial origen = new OrigenSocial(registro.proveedor(), registro.sujetoProveedor());
        return loadAccountRequestPort.porOrigenSocial(origen)
                .filter(solicitud -> solicitud.status().isPending())
                .map(solicitud -> {
                    log.info("[users.CompletarRegistroSocialService] completar: dos confirmaciones concurrentes "
                            + "de la misma identidad social ({}) -- se devuelve la solicitud {} que ya gano la "
                            + "carrera, en vez del 409 generico", registro.proveedor(), solicitud.id());
                    return solicitud.id();
                })
                .orElseThrow(() -> original);
    }
}
