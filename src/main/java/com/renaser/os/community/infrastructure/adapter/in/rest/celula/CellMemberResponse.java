package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.PerfilBasico;
import com.renaser.os.community.application.ports.in.celula.ConsultarMisCelulasUseCase.IntegranteDelGrupo;

/** Sin {@code coherenceScore} — vive en `puntajes_participante`, tabla de `points`
 * (docs/MODULO_COMMUNITY.md sec. 6).
 *
 * <p>{@code photoPath} (D-206, 2026-09-27): la ruta de la tarjeta con nombre de esa persona en el chat
 * del grupo, pedida con la sesión. Cuando viene, la lista de integrantes de la info del chat muestra la
 * tarjeta e ignora {@code avatarUrl}; cuando no, la foto subida o las iniciales. Quién la lleva lo
 * decide el modo del servidor ({@code CHAT_FOTO_DE_INTEGRANTES}): con {@code TARJETA}, todos; con
 * {@code FOTO_SUBIDA}, solo quien no subió foto. Solo en {@code /me/cells/{id}/members}; {@code null} en
 * {@code /me/cell/members} (el endpoint viejo) o si el grupo no tiene chat. Campo nuevo: las versiones
 * publicadas de la app lo ignoran (esquema {@code passthrough}). */
public record CellMemberResponse(String traineeId, String fullName, String avatarUrl, boolean isSelf,
                                 String photoPath) {

    /** {@code /me/cell/members}: sin foto. */
    public static CellMemberResponse from(PerfilBasico perfil, boolean isSelf) {
        return new CellMemberResponse(perfil.id().toString(), perfil.nombreCompleto(), perfil.avatarUrl(), isSelf,
                null);
    }

    /** {@code /me/cells/{id}/members}: con la ruta de su tarjeta (D-206). */
    public static CellMemberResponse from(IntegranteDelGrupo integrante, boolean isSelf) {
        PerfilBasico perfil = integrante.perfil();
        return new CellMemberResponse(perfil.id().toString(), perfil.nombreCompleto(), perfil.avatarUrl(), isSelf,
                integrante.rutaFoto());
    }
}
