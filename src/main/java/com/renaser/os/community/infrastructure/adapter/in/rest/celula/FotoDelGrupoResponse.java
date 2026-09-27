package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.celula.FotoDelGrupo;

/**
 * La foto propia de un grupo para quien puede cambiarla (D-212). Sin la ruta en el almacenamiento: la
 * foto la sirve el chat del grupo a sus integrantes, y a la app le alcanza con saber si hay una y desde
 * cuándo.
 *
 * @param photoChangedAt ISO-8601; {@code null} si el grupo usa la foto de Renaser
 */
public record FotoDelGrupoResponse(String cellId, String photoChangedAt) {

    static FotoDelGrupoResponse de(CelulaId grupo, FotoDelGrupo foto) {
        return new FotoDelGrupoResponse(grupo.toString(), foto != null ? foto.cambiadaEn().toString() : null);
    }
}
