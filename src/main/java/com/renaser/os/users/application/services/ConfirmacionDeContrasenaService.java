package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ConfirmacionDeContrasena;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort.CredencialParaLogin;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compara la contraseña escrita con la guardada, como el login ({@code AutenticacionService}) y «Eliminar mi cuenta»
 * ({@code CierrePropioService}). El límite de intentos lo pone quien llama: la consola Soporte TI cuenta los suyos.
 */
@Service
class ConfirmacionDeContrasenaService implements ConfirmacionDeContrasena {

    private final LoadUserPort loadUserPort;
    private final LoadCredencialPort loadCredencialPort;
    private final PasswordEncoder passwordEncoder;

    ConfirmacionDeContrasenaService(LoadUserPort loadUserPort, LoadCredencialPort loadCredencialPort,
                                    PasswordEncoder passwordEncoder) {
        this.loadUserPort = loadUserPort;
        this.loadCredencialPort = loadCredencialPort;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean esSuContrasena(UserId cuenta, String contrasena) {
        if (contrasena == null || contrasena.isEmpty()) {
            return false;
        }
        return loadUserPort.byId(cuenta)
                .filter(User::hasAccess)
                .flatMap(usuario -> loadCredencialPort.porEmail(usuario.email().value()))
                .filter(CredencialParaLogin::permiteLoginPorContrasena)
                .map(credencial -> passwordEncoder.matches(contrasena, credencial.hash()))
                .orElse(false);
    }
}
