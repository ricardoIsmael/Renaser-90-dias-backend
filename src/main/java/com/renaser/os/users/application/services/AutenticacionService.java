package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.CredencialesInvalidasException;
import com.renaser.os.shared.domain.RateLimitExceededException;
import com.renaser.os.users.application.ports.out.autenticacion.LimitarSolicitudesResetPort;
import java.time.Duration;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionUseCase;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort.CredencialParaLogin;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AutenticacionService implements IniciarSesionUseCase {

    /**
     * Hash valido de una contrasena que nadie usa, para comparar contra algo real cuando el
     * email no existe (ver {@link #iniciarSesion}). Cualquier hash BCrypt sirve — no hace falta
     * que corresponda a una contrasena en particular, solo que {@code passwordEncoder.matches}
     * tenga que hacer el trabajo criptografico completo.
     */
    private static final String HASH_SENUELO =
            "{bcrypt}$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5vTMBRfXHRT6QK0OEQmnFhFq6HZ0O";

    /**
     * Misma ventana que el resto de los limites del modulo (reset de contrasena, verificacion
     * de email, alta de solicitudes), para no tener cuatro criterios distintos conviviendo.
     *
     * <p>Los topes son mas altos que los de esos flujos, y a proposito: pedir un reseteo es
     * algo que se hace una vez cada mucho, mientras que iniciar sesion es rutina. Cinco
     * intentos por hora dejaria afuera a alguien que simplemente se equivoco de contrasena un
     * par de veces. Diez por correo sigue haciendo inviable adivinar una contrasena, que es lo
     * que hay que impedir.
     *
     * <p><b>Son numeros elegidos por quien escribio esto, no confirmados por el dueno del
     * proyecto.</b> Si resultan molestos en uso real, se suben; lo que no se puede es no tener
     * ninguno, que era el estado hasta ahora.
     */
    static final Duration VENTANA_RATE_LIMIT = Duration.ofHours(1);
    static final int LIMITE_POR_EMAIL = 10;
    static final int LIMITE_POR_IP = 50;

    private final LoadCredencialPort loadCredencialPort;
    private final LoadUserPort loadUserPort;
    private final PasswordEncoder passwordEncoder;
    private final LimitarSolicitudesResetPort limitarIntentosPort;

    public AutenticacionService(LoadCredencialPort loadCredencialPort, LoadUserPort loadUserPort,
                                 PasswordEncoder passwordEncoder,
                                 LimitarSolicitudesResetPort limitarIntentosPort) {
        this.loadCredencialPort = loadCredencialPort;
        this.loadUserPort = loadUserPort;
        this.passwordEncoder = passwordEncoder;
        this.limitarIntentosPort = limitarIntentosPort;
    }

    /**
     * Compara contra un hash señuelo aunque el email no exista o la cuenta no tenga contrasena,
     * para que {@code passwordEncoder.matches} corra siempre el mismo trabajo de BCrypt — sin
     * esto, el tiempo de respuesta delata si un email esta registrado (responder rapido cuando
     * no existe, lento cuando si) antes de que el mensaje de error llegue a decir nada.
     */
    @Override
    public User iniciarSesion(IniciarSesionCommand command) {
        requireDentroDelLimite(command);
        Optional<CredencialParaLogin> credencial = loadCredencialPort.porEmail(command.email());
        String hashParaComparar = credencial.filter(CredencialParaLogin::permiteLoginPorContrasena)
                .map(CredencialParaLogin::hash)
                .orElse(HASH_SENUELO);
        boolean coincide = passwordEncoder.matches(command.contrasena(), hashParaComparar);

        boolean loginValido = credencial.isPresent() && credencial.get().cuentaHabilitada()
                && credencial.get().permiteLoginPorContrasena() && coincide;
        if (!loginValido) {
            throw new CredencialesInvalidasException();
        }
        return loadUserPort.byId(credencial.get().usuarioId())
                .orElseThrow(() -> new IllegalStateException(
                        "Credencial sin usuario correspondiente: " + credencial.get().usuarioId()));
    }

    /**
     * Se cuenta ANTES de comparar la contrasena, y se cuenta TODO intento, no solo los
     * fallidos. Contar solo los fallidos deja abierta la puerta que se quiere cerrar: quien
     * prueba contrasenas al azar acierta o no, y si acierta ya entro — el limite tiene que
     * gastarse con el intento, no con su resultado.
     *
     * <p>El mensaje no distingue si el tope alcanzado fue el del correo o el de la IP: decirlo
     * le confirmaria a quien sondea que ese correo existe y esta siendo defendido.
     */
    private void requireDentroDelLimite(IniciarSesionCommand command) {
        if (!limitarIntentosPort.registrarIntento("login:email:" + command.email(),
                VENTANA_RATE_LIMIT, LIMITE_POR_EMAIL)) {
            throw new RateLimitExceededException("Demasiados intentos. Espera unos minutos.");
        }
        if (command.requestIp() != null && !limitarIntentosPort.registrarIntento(
                "login:ip:" + command.requestIp(), VENTANA_RATE_LIMIT, LIMITE_POR_IP)) {
            throw new RateLimitExceededException("Demasiados intentos. Espera unos minutos.");
        }
    }
}
