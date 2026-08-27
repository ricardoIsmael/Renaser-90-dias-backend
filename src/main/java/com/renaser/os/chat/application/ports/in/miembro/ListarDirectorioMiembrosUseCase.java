package com.renaser.os.chat.application.ports.in.miembro;

import com.renaser.os.shared.domain.UserId;

/**
 * Directorio de usuarios para iniciar un mensaje directo (#27, {@code GET /members}).
 *
 * <p>No hace falta una consulta nueva a `users` para tener el universo completo: TODO
 * usuario activo es participante de la conversacion GLOBAL por auto-join
 * (V1__baseline_renaser.sql:1293-1295, {@code UsuarioRegistradoChatListener}), asi que
 * el directorio se arma resolviendo esos participantes via {@code UserSummaryFinder}
 * EN LOTE.
 *
 * <p>Devuelve los CINCO roles — asi vive hoy en la app real
 * ({@code MiembrosPanel.tsx}: "ya no hace falta preguntar el rol... para decidir si se
 * le muestra"), no la restriccion a TRAINEE/MENTOR de un comentario mas viejo y
 * desactualizado de {@code chat/types.ts} del mismo repo.
 */
public interface ListarDirectorioMiembrosUseCase {

    /**
     * {@code query} filtra por nombre (contiene, sin distinguir mayusculas) si no viene
     * vacio; {@code null}/blank no filtra. {@code cursor} en {@code null} pide la primera
     * pagina. El actor nunca aparece en su propio directorio (no tiene sentido mandarse
     * un DM a uno mismo — mismo criterio que {@code obtenerOCrear}). Solo usuarios
     * {@code ACTIVE}: uno {@code SUSPENDED} no se puede usar como destino de un DM nuevo
     * de todas formas.
     */
    PaginaMiembros listar(UserId actorId, String query, UserId cursor, int limite);
}
