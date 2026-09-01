package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.RegistroPendienteSocialInvalidoException;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand;
import com.renaser.os.users.application.ports.in.autenticacion.CompletarRegistroSocialUseCase;
import com.renaser.os.users.application.ports.out.autenticacion.RegistroPendienteSocial;
import com.renaser.os.users.application.ports.out.autenticacion.TokenRegistroPendienteSocialPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import org.springframework.stereotype.Service;

/**
 * Segundo paso del alta social (docs/MODULO_AUTH.md §6.10, D-65). Clase propia y no un metodo
 * mas de {@link AutenticacionSocialService} (CLAUDE.MD §5.4.8, una clase por caso de uso):
 * aquel verifica una identidad y decide que camino sigue; este SOLO sabe convertir un registro
 * pendiente ya verificado en una {@code AccountRequest}.
 */
@Service
public class CompletarRegistroSocialService implements CompletarRegistroSocialUseCase {

    private final TokenRegistroPendienteSocialPort tokenRegistroPendienteSocialPort;
    private final SubmitAccountRequestUseCase submitAccountRequestUseCase;
    private final TokenVerificacionEmailPort tokenVerificacionEmailPort;

    public CompletarRegistroSocialService(TokenRegistroPendienteSocialPort tokenRegistroPendienteSocialPort,
                                           SubmitAccountRequestUseCase submitAccountRequestUseCase,
                                           TokenVerificacionEmailPort tokenVerificacionEmailPort) {
        this.tokenRegistroPendienteSocialPort = tokenRegistroPendienteSocialPort;
        this.submitAccountRequestUseCase = submitAccountRequestUseCase;
        this.tokenVerificacionEmailPort = tokenVerificacionEmailPort;
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

        return submitAccountRequestUseCase.submit(SubmitAccountRequestCommand.porProveedorSocial(registro.email(),
                command.fullName(), command.phone(), command.city(), verificationToken, command.requestIp(),
                registro.proveedor(), registro.sujetoProveedor()));
    }
}
