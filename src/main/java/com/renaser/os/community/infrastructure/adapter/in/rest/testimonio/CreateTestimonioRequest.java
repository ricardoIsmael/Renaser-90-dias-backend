package com.renaser.os.community.infrastructure.adapter.in.rest.testimonio;

/**
 * Formulario publico — puede llegar sin sesion (testimonios/repository.ts:24-37).
 * {@code wallPostId} presente => el controller lo trata como una promocion (solo admin,
 * testimonios/route.ts:22-32) y {@code nombre}/{@code texto} no hacen falta: se derivan de
 * la publicacion. {@code formation} (spec 024): la formacion del testimonio escrito a mano; la elige Administración, que no pertenece a una.
 * Sin validacion Bean aca a proposito — el codigo viejo usaba dos schemas
 * Zod distintos segun la rama; el controller decide cual aplica.
 */
public record CreateTestimonioRequest(String nombre, String rol, String texto, Integer estrellas,
                                       String wallPostId, String formation) {

    public CreateTestimonioRequest(String nombre, String rol, String texto, Integer estrellas, String wallPostId) {
        this(nombre, rol, texto, estrellas, wallPostId, null);
    }
}
