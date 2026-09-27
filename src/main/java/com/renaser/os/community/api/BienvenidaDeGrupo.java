package com.renaser.os.community.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las bienvenidas de grupo que faltan dar (OPE-01-01, D-191): cada pertenencia de aprendiz a un
 * grupo estable lleva su marca en {@code asignaciones_celula.bienvenida_enviada_en} (V71).
 *
 * <p>Lo usa {@code chat}, que es quien escribe el mensaje: {@code community} dice a quién le falta y
 * guarda la marca; nunca el chat toca la tabla ajena.
 */
public interface BienvenidaDeGrupo {

    /**
     * Las pertenencias de aprendiz vigentes y sin bienvenida de un grupo ESTABLE, con su mentor
     * vigente. Vacío si el grupo es la recepción, no existe, no está operativo o no tiene mentor: en
     * ese último caso quedan pendientes y salen cuando el grupo tenga mentor.
     */
    Optional<Pendientes> pendientes(UUID grupoId, Instant instante);

    /**
     * Deja la marca si la pertenencia sigue abierta y sin marca. Se llama DENTRO de la transacción que
     * guarda el mensaje: o quedan los dos o ninguno.
     *
     * @return {@code true} si esta llamada la marcó; {@code false} si ya estaba marcada (otra entrega
     *         la dio) o la pertenencia se cerró: en los dos casos no se manda el mensaje
     */
    boolean marcarDada(UUID asignacionId, Instant instante);

    record Pendientes(UUID grupoId, UserId mentorId, List<Pendiente> aprendices) {
    }

    /**
     * @param desde el {@code inicio} de la pertenencia: {@code chat} no le da la bienvenida a quien
     *              entró hace días (D-204)
     */
    record Pendiente(UUID asignacionId, UserId aprendizId, Instant desde) {
    }
}
