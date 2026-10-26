package com.renaser.os.plataforma.domain.model.despliegue;

/**
 * No se puede empezar un despliegue de una funcionalidad que se usa sin cuenta (la que en el catálogo no tiene roles,
 * como el alta pública {@code registro}): la cubeta sale del id de la persona, y sin persona la regla no se aplica, así
 * que el despliegue no tendría efecto. La consola responde 409 con {@code codigo: "DESPLIEGUE_SIN_CUENTA"}.
 *
 * <p>Extiende {@link IllegalStateException} para que, si algún día la lanza otro camino, siga siendo un 409.
 */
public final class DespliegueSinCuentaException extends IllegalStateException {

    public static final String CODIGO = "DESPLIEGUE_SIN_CUENTA";
    public static final String MENSAJE = "Esta funcionalidad se usa sin cuenta; un despliegue no tendría efecto.";

    private final String clave;

    public DespliegueSinCuentaException(String clave) {
        super(MENSAJE);
        this.clave = clave;
    }

    public String clave() {
        return clave;
    }
}
