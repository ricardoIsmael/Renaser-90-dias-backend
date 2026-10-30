package com.renaser.os.rocks.domain.model.cierre;

/**
 * «¿Qué pasó?»: por qué una acción del día quedó pendiente (spec 017, FR-002). Los cuatro valores los eligió el dueño en la vista
 * previa del 2026-10-29. El nombre de la constante es el valor que guarda {@code rocas_diarias.cierre_motivo} y el que viaja en
 * el JSON; el texto que lee la persona lo pone la app.
 */
public enum MotivoDeAccionPendiente {
    /** «No me alcanzó el tiempo». */
    SIN_TIEMPO,
    /** «Surgió otra cosa». */
    SURGIO_OTRA_COSA,
    /** «Me faltó energía». */
    SIN_ENERGIA,
    /** «Lo dejé para después». */
    PARA_DESPUES;

    /** Un valor que no es ninguno de los cuatro es un error del que llama (400), no un valor por defecto. */
    public static MotivoDeAccionPendiente desde(String valor) {
        for (MotivoDeAccionPendiente motivo : values()) {
            if (motivo.name().equals(valor)) {
                return motivo;
            }
        }
        throw new IllegalArgumentException("Elige qué pasó con la acción: ese motivo no existe");
    }
}
