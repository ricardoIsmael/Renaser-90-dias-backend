package com.renaser.os.community.domain.model.sobrenombre;

import java.util.List;

/**
 * Spec 046: la lista base de palabras que no van en un sobrenombre. Administración suma las suyas
 * (`palabras_prohibidas_sobrenombre`). Se comparan sin tildes ni mayúsculas, y también dentro de otra palabra.
 *
 * <p>Son nombres de roles y de la plataforma (para que nadie se haga pasar por el equipo) y groserías e insultos comunes en Perú.
 */
public final class PalabrasProhibidasBase {

    public static final List<String> PALABRAS = List.of(
            // el equipo y la plataforma
            "admin", "administrador", "administracion", "renaser", "mentor", "mentora", "lider", "soporte", "alquimista", "moderador",
            "oficial", "staff",
            // groserías e insultos
            "puta", "puto", "mierda", "carajo", "cojudo", "cojuda", "huevon", "huevona", "cabron", "conchatumare",
            "pendejo", "pendeja", "maricon", "cholo", "idiota", "imbecil", "estupido", "estupida", "tarado", "tarada",
            "zorra", "perra", "verga", "pinga", "chucha", "csm", "ctm", "ptm", "mrd", "nazi", "hitler", "retrasado");

    private PalabrasProhibidasBase() {
    }
}
