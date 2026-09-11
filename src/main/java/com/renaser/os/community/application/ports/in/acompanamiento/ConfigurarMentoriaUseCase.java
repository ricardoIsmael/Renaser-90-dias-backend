package com.renaser.os.community.application.ports.in.acompanamiento;

import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.UUID;

/**
 * Administración de la operación de una cohorte: cupo, cadencia, zona, umbral de aviso y quiénes
 * atienden la recepción.
 *
 * <p>Nada de esto crea usuarios ni cambia roles. Designar una función de acompañamiento y dar de
 * alta una cuenta son cosas distintas; mezclarlas convertiría un error de tipeo en una cuenta
 * fantasma (clarifications.md).
 */
public interface ConfigurarMentoriaUseCase {

    PoliticaConfigurada consultar(UserId actorId, CohorteId cohorteId);

    PoliticaConfigurada reconfigurar(ReconfigurarPolitica comando);

    GuiasConfigurados reemplazarGuias(ReemplazarGuias comando);

    /**
     * @param versionEsperada la que el administrador tenía en pantalla. Si no coincide, otro la
     *                        editó mientras tanto y su cambio no se pisa en silencio.
     */
    record ReconfigurarPolitica(UserId actorId, CohorteId cohorteId, int capacidadCelula,
                                 String cadenciaRotacion, String zonaHoraria, int diaTraslado,
                                 int diasSinActividadAlerta, int versionEsperada) {
    }

    /**
     * @param referencias cada guía por {@code userId} O {@code email}, nunca los dos a la vez.
     * @param celulaRecepcionId dónde atienden. Si viene null se conserva la que ya estaba.
     */
    record ReemplazarGuias(UserId actorId, CohorteId cohorteId, UUID celulaRecepcionId,
                            List<ReferenciaDeUsuario> referencias) {
    }

    /** Exactamente una de las dos formas de nombrar a alguien. Ambas o ninguna es un error. */
    record ReferenciaDeUsuario(UUID userId, String email) {
    }

    record PoliticaConfigurada(UUID cohorteId, int capacidadCelula, String cadenciaRotacion, String zonaHoraria,
                                int diaTraslado, int diasSinActividadAlerta, UUID celulaRecepcionId,
                                int version) {
    }

    /** @param guias los que quedaron designados, tras el reemplazo. */
    record GuiasConfigurados(UUID cohorteId, UUID celulaRecepcionId, List<UUID> guias) {
    }
}
