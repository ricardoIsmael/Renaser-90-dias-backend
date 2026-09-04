package com.renaser.os.users.application.ports.out.ajustediaprograma;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.ajustediaprograma.AjusteDiaPrograma;

import java.util.Optional;

/**
 * El ultimo ajuste de un participante — lo que el panel admin muestra junto al dia para
 * que el numero no aparezca sin explicacion ("dia 34, ajustado por Ana el 03/09: viaje").
 * Deliberadamente NO es "el historial completo": esa lectura todavia no la pide ninguna
 * pantalla, y un puerto se agrega cuando hay un consumidor, no por si acaso.
 */
public interface LoadUltimoAjusteDiaProgramaPort {

    Optional<AjusteDiaPrograma> ultimoDe(UserId participanteId);
}
