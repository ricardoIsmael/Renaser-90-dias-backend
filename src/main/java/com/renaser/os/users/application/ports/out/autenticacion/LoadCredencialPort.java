package com.renaser.os.users.application.ports.out.autenticacion;

import com.renaser.os.shared.domain.UserId;

import java.util.Optional;

public interface LoadCredencialPort {

    /**
     * Trae de una sola query lo unico que el login necesita. El estado viaja junto con el hash a
     * proposito: verificar la contrasena y despues descubrir que la cuenta esta suspendida serian
     * dos viajes a la base en el camino mas transitado del sistema.
     */
    Optional<CredencialParaLogin> porEmail(String email);

    /**
     * Un booleano y no {@code UserStatus} a proposito. La columna `estado` admite un tercer valor,
     * INACTIVO, que todavia no tiene equivalente en el dominio (pregunta abierta R-3) y que hace
     * que el mapper de `user` lance una excepcion — un 500 en la pantalla de login. Preguntando
     * "¿habilita el acceso?" en vez de "¿cual es el estado?", cualquier valor que no sea ACTIVO
     * niega el acceso por construccion, sin inventar que significa INACTIVO.
     *
     * <p>{@code hash} puede ser null: la cuenta existe pero solo entra por proveedor social.
     */
    record CredencialParaLogin(UserId usuarioId, String hash, boolean cuentaHabilitada) {

        public boolean permiteLoginPorContrasena() {
            return hash != null && !hash.isBlank();
        }
    }
}
