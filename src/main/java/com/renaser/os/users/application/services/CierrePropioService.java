package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.CodigoVerificacionInvalidoException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.eliminacion.CerrarMiCuentaUseCase;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort.CredencialParaLogin;
import com.renaser.os.users.application.ports.out.eliminacion.CodigoEliminarCuentaPort;
import com.renaser.os.users.domain.model.user.EstadoBajaCuenta;
import com.renaser.os.users.domain.model.user.RegistroDeEliminacion;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Yo → «Eliminar mi cuenta» (D-243). Sin {@code @Transactional} a proposito: verificar la contraseña
 * (BCrypt, lento) y mandar el correo no van dentro de una transaccion; la transaccion es la del cierre
 * ({@link CierreDeCuentaService#cerrar}).
 *
 * <p>Los dos topes por cuenta frenan dos abusos distintos: probar contraseñas desde una sesion robada
 * (que es justo lo que la contraseña tiene que impedir aca) y usar el boton para llenarle el buzon a
 * alguien. Se cuentan con el mismo contador de Redis que el reset, en claves propias.
 */
@Service
class CierrePropioService implements CerrarMiCuentaUseCase {

    static final Duration VENTANA = Duration.ofHours(1);
    static final int INTENTOS_POR_HORA = 5;
    static final int CODIGOS_POR_HORA = 5;

    private final RequireActiveUserGuard requireActiveUserGuard;
    private final LoadCredencialPort loadCredencialPort;
    private final PasswordEncoder passwordEncoder;
    private final CodigoEliminarCuentaPort codigoPort;
    private final EnviarEmailPort enviarEmailPort;
    private final LimitarSolicitudesResetPort limitador;
    private final CierreDeCuentaService cierre;

    CierrePropioService(RequireActiveUserGuard requireActiveUserGuard, LoadCredencialPort loadCredencialPort,
                        PasswordEncoder passwordEncoder, CodigoEliminarCuentaPort codigoPort,
                        EnviarEmailPort enviarEmailPort, LimitarSolicitudesResetPort limitador,
                        CierreDeCuentaService cierre) {
        this.requireActiveUserGuard = requireActiveUserGuard;
        this.loadCredencialPort = loadCredencialPort;
        this.passwordEncoder = passwordEncoder;
        this.codigoPort = codigoPort;
        this.enviarEmailPort = enviarEmailPort;
        this.limitador = limitador;
        this.cierre = cierre;
    }

    @Override
    public ComoConfirmar comoConfirmar(UserId cuentaId) {
        User cuenta = requireActiveUserGuard.of(cuentaId);
        Metodo metodo = conContrasena(cuenta).isPresent() ? Metodo.CONTRASENA : Metodo.CODIGO;
        return new ComoConfirmar(metodo, cierre.diasDeGracia());
    }

    @Override
    public void enviarCodigo(UserId cuentaId) {
        User cuenta = requireActiveUserGuard.of(cuentaId);
        exigirCupo("eliminar-cuenta-codigos:" + cuentaId, CODIGOS_POR_HORA);
        String codigo = codigoPort.generarCodigo(cuenta.email().value(), VerificacionEmailService.VIGENCIA_CODIGO);
        enviarEmailPort.enviarCodigoEliminarCuenta(cuenta.email().value(), codigo);
    }

    @Override
    public EstadoBajaCuenta cerrar(CerrarMiCuentaCommand command) {
        User cuenta = requireActiveUserGuard.of(command.cuenta());
        exigirCupo("eliminar-cuenta-intentos:" + command.cuenta(), INTENTOS_POR_HORA);
        exigirQueEsElla(cuenta, command);
        return cierre.cerrar(cuenta, RegistroDeEliminacion.Via.APP);
    }

    private void exigirQueEsElla(User cuenta, CerrarMiCuentaCommand command) {
        if (tieneTexto(command.contrasena())) {
            String hash = conContrasena(cuenta).map(CredencialParaLogin::hash).orElse(null);
            if (hash == null || !passwordEncoder.matches(command.contrasena(), hash)) {
                throw new IllegalArgumentException("La contraseña no es correcta");
            }
            return;
        }
        if (!tieneTexto(command.codigo())) {
            throw new IllegalArgumentException("Escribe tu contraseña o el código que te enviamos");
        }
        if (!codigoPort.verificarCodigo(cuenta.email().value(), command.codigo().trim(),
                VerificacionEmailService.MAX_INTENTOS)) {
            throw new CodigoVerificacionInvalidoException();
        }
    }

    private Optional<CredencialParaLogin> conContrasena(User cuenta) {
        return loadCredencialPort.porEmail(cuenta.email().value())
                .filter(CredencialParaLogin::permiteLoginPorContrasena);
    }

    private void exigirCupo(String clave, int maximo) {
        if (!limitador.registrarIntento(clave, VENTANA, maximo)) {
            throw new RateLimitExceededException("Demasiados intentos. Prueba de nuevo en una hora.");
        }
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
