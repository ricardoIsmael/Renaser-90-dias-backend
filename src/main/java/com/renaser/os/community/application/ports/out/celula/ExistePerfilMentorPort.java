package com.renaser.os.community.application.ports.out.celula;

import com.renaser.os.shared.domain.UserId;

/**
 * `celulas.mentor_id` referencia `perfiles_mentor.usuario_id` (FK RESTRICT,
 * V1__baseline_renaser.sql:245) — `perfiles_mentor` es un perfil de `users` (CLAUDE.MD
 * sec. 5.3.2), asi que este modulo NUNCA lo crea (ver CM-08, docs/MODULO_COMMUNITY.md
 * sec. 6): solo comprueba que ya exista, para devolver un 422 legible en vez de dejar
 * que la foranea reviente con un error de Postgres crudo (mismo espiritu que
 * `findEligibleCellLeader` en community/repository.ts:480-494, que en el codigo viejo
 * SI creaba el perfil — acá no, porque la escritura no es de este modulo).
 */
public interface ExistePerfilMentorPort {

    boolean existe(UserId usuarioId);
}
