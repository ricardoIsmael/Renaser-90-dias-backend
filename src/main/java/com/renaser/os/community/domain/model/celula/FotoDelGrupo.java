package com.renaser.os.community.domain.model.celula;

import java.time.Instant;
import java.util.Objects;

/**
 * La foto propia de un grupo (D-212, decisión del dueño del 2026-09-27: la cambian «Admin y el mentor de
 * ese grupo»). Sin foto propia, el grupo usa la de Renaser: la tarjeta de Canva sin nombre del APK.
 *
 * @param ruta       la clave del objeto en el almacenamiento; privado, lo sirve el backend con sesión
 * @param cambiadaEn cuándo se eligió; va en la ruta que recibe la app ({@code ?v=}) para que el teléfono
 *                   la vuelva a bajar cuando cambia
 */
public record FotoDelGrupo(String ruta, Instant cambiadaEn) {

    public FotoDelGrupo {
        if (ruta == null || ruta.isBlank()) {
            throw new IllegalArgumentException("La foto del grupo necesita su ruta en el almacenamiento");
        }
        Objects.requireNonNull(cambiadaEn, "cambiadaEn es obligatorio");
    }

    /**
     * Una foto nueva para el grupo, con una clave que no se repite: cada cambio es otro objeto, así
     * nunca se pisa uno que un teléfono tenga guardado, y la anterior se puede borrar sin carreras.
     */
    public static FotoDelGrupo nueva(CelulaId grupo, Instant ahora) {
        Objects.requireNonNull(grupo, "grupo es obligatorio");
        return new FotoDelGrupo("grupos/" + grupo + "/foto-" + ahora.toEpochMilli() + ".jpg", ahora);
    }
}
