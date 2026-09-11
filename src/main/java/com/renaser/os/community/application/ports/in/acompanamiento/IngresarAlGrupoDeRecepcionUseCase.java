package com.renaser.os.community.application.ports.in.acompanamiento;

import com.renaser.os.shared.domain.UserId;

/**
 * Mete a quien acaba de registrarse en el grupo de bienvenida que este vigente.
 *
 * <p>Sin comando: no lo invoca una persona, lo dispara el alta. El grupo lo crea y lo programa el
 * administrador —con su mentor y sus siete dias—; lo unico automatico es la ENTRADA.
 */
public interface IngresarAlGrupoDeRecepcionUseCase {

    /** Idempotente: repetir la llamada no abre un segundo intervalo. */
    void ingresar(UserId usuarioId);
}
