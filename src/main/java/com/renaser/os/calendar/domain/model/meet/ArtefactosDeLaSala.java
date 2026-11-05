package com.renaser.os.calendar.domain.model.meet;

/**
 * Lo que la sala de Meet guarda sola de la sesión: la grabación, la transcripción y las notas automáticas (spec 040, decisión 6 del dueño del
 * 2026-11-05, FR-036). Se pide con la Meet REST API ({@code config.artifactConfig}) y se lee de vuelta, porque un 200 no prueba que quedó.
 *
 * <p>Cada valor tiene tres estados: {@code true} = está encendido en Google; {@code false} = se pidió y Google no lo dejó (la cuenta conectada no
 * tiene el plan que hace falta); {@code null} = no se pidió o todavía no se sabe. Usado como PEDIDO, {@code true} = pedirlo y {@code null} = no
 * tocarlo (nunca se pide apagar nada).
 */
public record ArtefactosDeLaSala(Boolean grabacion, Boolean transcripcion, Boolean notas) {

    public static final ArtefactosDeLaSala NADA = new ArtefactosDeLaSala(null, null, null);

    /** El pedido según la configuración: lo que está en {@code true} se pide; lo demás no se toca. */
    public static ArtefactosDeLaSala pedir(boolean grabar, boolean transcribir, boolean notas) {
        return new ArtefactosDeLaSala(grabar ? Boolean.TRUE : null, transcribir ? Boolean.TRUE : null, notas ? Boolean.TRUE : null);
    }

    /** Hay al menos algo que pedir o que se sabe. */
    public boolean algo() {
        return grabacion != null || transcripcion != null || notas != null;
    }

    /** Alguno se pidió y Google no lo dejó: la cuenta conectada no tiene el plan (FR-037). */
    public boolean algunoRechazado() {
        return Boolean.FALSE.equals(grabacion) || Boolean.FALSE.equals(transcripcion) || Boolean.FALSE.equals(notas);
    }
}
