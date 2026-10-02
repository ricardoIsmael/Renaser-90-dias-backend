package com.renaser.os.users.application.ports.in.eliminacion;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.user.EstadoBajaCuenta;

import java.util.Objects;

/**
 * La persona elimina su cuenta desde la app, Yo → «Eliminar mi cuenta» (decision 1 del dueño, D-243).
 * Confirma con su contraseña o, si su cuenta no tiene (entra con Google o Apple), con un codigo que le
 * llega al correo. La cuenta se cierra al instante y se borra para siempre al vencer la gracia.
 */
public interface CerrarMiCuentaUseCase {

    /** Como tiene que confirmar esta cuenta, y cuantos dias de gracia hay. */
    ComoConfirmar comoConfirmar(UserId cuenta);

    /** Manda al correo de la cuenta un codigo de 6 digitos para confirmar. */
    void enviarCodigo(UserId cuenta);

    /** Cierra la cuenta. 400 si la contraseña o el codigo no sirven; 409 si es el ultimo ADMIN activo. */
    EstadoBajaCuenta cerrar(CerrarMiCuentaCommand command);

    enum Metodo { CONTRASENA, CODIGO }

    record ComoConfirmar(Metodo metodo, int diasDeGracia) {
    }

    /** {@code contrasena} o {@code codigo}: con uno alcanza. */
    record CerrarMiCuentaCommand(UserId cuenta, String contrasena, String codigo) {

        public CerrarMiCuentaCommand {
            Objects.requireNonNull(cuenta, "cuenta es obligatoria");
        }
    }
}
