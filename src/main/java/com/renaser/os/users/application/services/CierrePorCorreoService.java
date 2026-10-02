package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.CodigoVerificacionInvalidoException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.users.application.ports.in.eliminacion.CerrarCuentaPorCorreoUseCase;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.eliminacion.CodigoEliminarCuentaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.EstadoBajaCuenta;
import com.renaser.os.users.domain.model.user.RegistroDeEliminacion;
import com.renaser.os.users.domain.model.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * La pagina web publica para pedir la eliminacion (D-243, requisito de Google Play). Mismo contrato de
 * no-enumeracion que la recuperacion de contraseña ({@link ResetContrasenaService}):
 * <ul>
 *   <li>pedir el codigo responde igual exista o no la cuenta, y un correo que falla al enviarse se
 *       anota en el log en vez de volverse un 503 que solo aparece cuando la cuenta existe;</li>
 *   <li>confirmar consume el codigo ANTES de buscar la cuenta, asi «correo sin cuenta» y «codigo
 *       equivocado» cuestan lo mismo y fallan igual.</li>
 * </ul>
 * Topes: por origen y por (origen, correo) para las peticiones, y por correo para los envios.
 */
@Service
class CierrePorCorreoService implements CerrarCuentaPorCorreoUseCase {

    private static final Logger log = LoggerFactory.getLogger(CierrePorCorreoService.class);

    static final Duration VENTANA = Duration.ofHours(1);
    static final int LIMITE_POR_ORIGEN = 20;
    static final int LIMITE_POR_ORIGEN_Y_CORREO = 5;
    static final int ENVIOS_POR_CORREO = 10;
    private static final String MENSAJE_LIMITE = "Demasiados intentos. Prueba de nuevo en una hora.";

    private final LoadUserPort loadUserPort;
    private final CodigoEliminarCuentaPort codigoPort;
    private final EnviarEmailPort enviarEmailPort;
    private final LimitarSolicitudesResetPort limitador;
    private final CierreDeCuentaService cierre;

    CierrePorCorreoService(LoadUserPort loadUserPort, CodigoEliminarCuentaPort codigoPort,
                           EnviarEmailPort enviarEmailPort, LimitarSolicitudesResetPort limitador,
                           CierreDeCuentaService cierre) {
        this.loadUserPort = loadUserPort;
        this.codigoPort = codigoPort;
        this.enviarEmailPort = enviarEmailPort;
        this.limitador = limitador;
        this.cierre = cierre;
    }

    @Override
    public void solicitarCodigo(String email, String requestIp) {
        String correo = normalizado(email);
        exigirCupoDelOrigen(correo, requestIp);
        cuentaDe(correo).ifPresent(cuenta -> {
            if (limitador.registrarIntento("eliminar-cuenta-envios:" + correo, VENTANA, ENVIOS_POR_CORREO)) {
                enviarSinDelatar(correo);
            }
        });
    }

    @Override
    public EstadoBajaCuenta confirmar(String email, String codigo, String requestIp) {
        String correo = normalizado(email);
        exigirCupoDelOrigen(correo, requestIp);
        if (codigo == null || !codigoPort.verificarCodigo(correo, codigo.trim(), VerificacionEmailService.MAX_INTENTOS)) {
            throw new CodigoVerificacionInvalidoException();
        }
        User cuenta = cuentaDe(correo).orElseThrow(CodigoVerificacionInvalidoException::new);
        return cierre.cerrar(cuenta, RegistroDeEliminacion.Via.WEB);
    }

    private void enviarSinDelatar(String correo) {
        try {
            String codigo = codigoPort.generarCodigo(correo, VerificacionEmailService.VIGENCIA_CODIGO);
            enviarEmailPort.enviarCodigoEliminarCuenta(correo, codigo);
        } catch (RuntimeException e) {
            log.error("[users.CierrePorCorreo] no salio el codigo para eliminar una cuenta", e);
        }
    }

    private Optional<User> cuentaDe(String correo) {
        try {
            return loadUserPort.byEmail(new Email(correo));
        } catch (IllegalArgumentException correoMalFormado) {
            return Optional.empty();
        }
    }

    private void exigirCupoDelOrigen(String correo, String requestIp) {
        if (!limitador.registrarIntento("eliminar-cuenta-ip:" + OrigenDeLaPeticion.de(requestIp), VENTANA,
                LIMITE_POR_ORIGEN)
                || !limitador.registrarIntento(OrigenDeLaPeticion.claveConEmail("eliminar-cuenta-origen:",
                requestIp, correo), VENTANA, LIMITE_POR_ORIGEN_Y_CORREO)) {
            throw new RateLimitExceededException(MENSAJE_LIMITE);
        }
    }

    private static String normalizado(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
