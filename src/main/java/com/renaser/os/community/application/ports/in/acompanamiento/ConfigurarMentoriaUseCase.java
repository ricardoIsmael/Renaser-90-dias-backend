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
     * Quiénes son HOY los guías de la recepción (D-242). Hasta esto, la designación solo se podía
     * escribir: no había forma de leerla sin ir a la base, y por eso no existía pantalla.
     *
     * @param celulaRecepcionId de qué grupo de recepción. {@code null} = el que la política ya
     *                          tiene designado; si tampoco hay, la respuesta va sin grupo y sin guías.
     */
    GuiasVigentes consultarGuias(UserId actorId, CohorteId cohorteId, UUID celulaRecepcionId);

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

    /** @param celulaRecepcionId {@code null} solo cuando la cohorte no tiene recepción designada. */
    record GuiasVigentes(UUID cohorteId, UUID celulaRecepcionId, List<GuiaVigente> guias) {
    }

    /**
     * Un guía con lo justo para reconocerlo en una lista. {@code rol} y {@code estado} van como
     * texto del enum; {@code null} los tres si la cuenta ya no existe (la designación sí).
     */
    record GuiaVigente(UUID usuarioId, String nombre, String rol, String estado) {
    }
}
