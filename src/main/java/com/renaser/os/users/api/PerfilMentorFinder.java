package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Lo que `users` expone del perfil de un mentor: si existe y en que se especializa.
 *
 * <p><b>Por que existe.</b> `community` necesita la especialidad para armar el selector de
 * mentor —el administrador elige por ella (SDD 003, ARF-05)— y hasta ahora la unica via era
 * {@code ExistePerfilMentorPersistenceAdapter}, que manda un {@code SELECT} nativo contra
 * {@code renaser.perfiles_mentor}: una tabla de OTRO modulo. Eso funciona hasta que `users`
 * renombra una columna y el que se rompe es `community`, en runtime y sin que nada lo avise en
 * compilacion.
 *
 * <p>La lectura en lote no es una optimizacion prematura: el selector pide entre diez y
 * cincuenta mentores de una vez, y preguntar uno por uno es el N+1 que
 * {@code ParticipacionProgramaFinder} ya vino a resolver para el mismo panel.
 */
public interface PerfilMentorFinder {

    Optional<PerfilMentor> porUsuario(UserId usuarioId);

    /** Solo los que TIENEN perfil: un id sin perfil no aparece en el mapa, no viaja con nulos. */
    Map<UserId, PerfilMentor> porUsuarios(Collection<UserId> usuarioIds);

    /**
     * @param especialidad {@code null} = no la declaro todavia. Es un estado valido y frecuente
     *                     —asi quedaron los perfiles anteriores a V48— y quien lo lea tiene que
     *                     mostrarlo como "sin especialidad definida", no inventarle una.
     */
    record PerfilMentor(UserId usuarioId, EspecialidadMentor especialidad) {
    }
}
