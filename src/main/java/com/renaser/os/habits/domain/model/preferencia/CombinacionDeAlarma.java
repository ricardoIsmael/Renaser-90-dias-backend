package com.renaser.os.habits.domain.model.preferencia;

/**
 * Como suena la alarma de un habito (spec 009, D-283, 2026-10-20): sonido, vibracion, voz que dice el
 * habito y repetir si no se atiende. Es la combinacion YA RESUELTA —las cuatro respuestas existen— y siempre
 * valida; lo que una capa (el habito o un dia de la semana) deja sin decir es {@link AjusteDeAlarma}.
 *
 * <p>Dos reglas, las que pidio el dueno para quien tiene 40 anos o mas y no debe quedarse sin alarma sin
 * querer: sonido y vibracion NO pueden quedar los dos apagados (quien quiere silencio usa «Sin aviso», que
 * es el recordatorio), y la voz es un sonido, asi que necesita el sonido encendido.
 *
 * <p>{@link #COMO_HOY} es lo que suena sin que nadie haya elegido nada: el tono general de Yo → Alarmas con
 * vibracion, sin voz propia del habito y sin repeticion. El tono de ese ajuste vive solo en el telefono: el
 * servidor no lo conoce, asi que «voz» aqui significa la voz que dice el nombre del habito, apagada por
 * defecto.
 */
public record CombinacionDeAlarma(boolean sonido, boolean vibracion, boolean voz, boolean repetir) {

    /** Lo que suena hoy: tono y vibracion, sin la voz del habito y sin repetir. */
    public static final CombinacionDeAlarma COMO_HOY = new CombinacionDeAlarma(true, true, false, false);

    public CombinacionDeAlarma {
        requireValida(sonido, vibracion, voz);
    }

    public static CombinacionDeAlarma de(boolean sonido, boolean vibracion, boolean voz, boolean repetir) {
        return new CombinacionDeAlarma(sonido, vibracion, voz, repetir);
    }

    /** Esta combinacion con lo que fija esa capa encima; una capa nula o vacia no cambia nada. */
    public CombinacionDeAlarma con(AjusteDeAlarma capa) {
        return capa == null ? this : capa.aplicadoA(this);
    }

    /**
     * Las dos reglas, para quien las pregunta con campos que pueden faltar ({@code null} = no se dijo, no se
     * puede romper). Un solo lugar: la combinacion completa y el ajuste de una capa no pueden discrepar.
     */
    static void requireValida(Boolean sonido, Boolean vibracion, Boolean voz) {
        if (Boolean.FALSE.equals(sonido) && Boolean.FALSE.equals(vibracion)) {
            throw new IllegalArgumentException("Al menos el sonido o la vibracion tienen que quedar encendidos");
        }
        if (Boolean.TRUE.equals(voz) && Boolean.FALSE.equals(sonido)) {
            throw new IllegalArgumentException("La voz necesita el sonido encendido");
        }
    }
}
