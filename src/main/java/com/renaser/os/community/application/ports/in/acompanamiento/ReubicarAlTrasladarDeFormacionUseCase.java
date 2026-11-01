package com.renaser.os.community.application.ports.in.acompanamiento;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

/**
 * Lo que hace {@code community} cuando Administración pasa a un aprendiz a otra formación (spec 022): lo saca de TODOS los grupos de la
 * formación anterior y lo entra al grupo de bienvenida de la nueva. Lo dispara el evento de {@code users}; el puntero
 * {@code participantes_programa.celula_id} y el mentor ya los dejó vacíos {@code users}.
 */
public interface ReubicarAlTrasladarDeFormacionUseCase {

    /** Idempotente: repetir el aviso no cierra ni abre nada de más. */
    void reubicar(UserId aprendiz, Formacion hacia, Instant trasladadoEn);
}
