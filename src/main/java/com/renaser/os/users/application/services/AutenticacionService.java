package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.CredencialesInvalidasException;
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

    private final LoadCredencialPort loadCredencialPort;
    private final LoadUserPort loadUserPort;
    private final PasswordEncoder passwordEncoder;

    public AutenticacionService(LoadCredencialPort loadCredencialPort, LoadUserPort loadUserPort,
                                 PasswordEncoder passwordEncoder) {
        this.loadCredencialPort = loadCredencialPort;
        this.loadUserPort = loadUserPort;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Compara contra un hash señuelo aunque el email no exista o la cuenta no tenga contrasena,
     * para que {@code passwordEncoder.matches} corra siempre el mismo trabajo de BCrypt — sin
     * esto, el tiempo de respuesta delata si un email esta registrado (responder rapido cuando
     * no existe, lento cuando si) antes de que el mensaje de error llegue a decir nada.
     */
    @Override
    public User iniciarSesion(IniciarSesionCommand command) {
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
}
