package com.renaser.os.community.application.ports.in.acompanamiento;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

/**
 * Mete a quien acaba de registrarse en el grupo de bienvenida que este vigente.
 *
 * <p>Sin comando: no lo invoca una persona, lo dispara el alta. El grupo lo crea y lo programa el
 * administrador —con su mentor y sus siete dias—; lo unico automatico es la ENTRADA.
 */
public interface IngresarAlGrupoDeRecepcionUseCase {

    /** Idempotente: repetir la llamada no abre un segundo intervalo. Entra al de bienvenida de la formación de la persona (spec 022). */
    void ingresar(UserId usuarioId);

    /**
     * Entra al grupo de bienvenida de la formación a la que lo pasaron (spec 022, Q1-A). Idempotente por traslado: la clave lleva el momento del
     * traslado, así que repetir el aviso no abre otro intervalo y un segundo traslado a la misma formación sí entra de nuevo.
     */
    void ingresarPorTraslado(UserId usuarioId, Formacion formacion, Instant trasladadoEn);
}
