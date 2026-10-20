package com.renaser.os.habits.domain.model.preferencia;

/**
 * Lo que UNA capa —el habito entero o un dia de la semana— dice de como suena la alarma (spec 009, D-283).
 * Cada campo en {@code null} es «no digo nada, hereda»: se resuelve campo por campo y de abajo hacia arriba,
 * la misma idea que la hora y el aviso de la 008. Sin capas, rige {@link CombinacionDeAlarma#COMO_HOY}.
 *
 * <p>Lo que se GUARDA desde la app es siempre una combinacion entera ({@link #de}): una vez que un dia tiene
 * su alarma propia queda fija aunque cambie la general, como un aviso propio. Los campos sueltos existen para
 * leer con tolerancia una fila que traiga solo algunos, y para que la regla de la herencia sea una sola.
 *
 * <p>Dentro de una misma capa se aplican las dos reglas de {@link CombinacionDeAlarma} a lo que SE dijo; la
 * mezcla con otras capas nunca rompe nada, se arregla en {@link #aplicadoA} (la voz sin sonido se apaga, y si
 * la mezcla dejara sonido y vibracion apagados manda lo de abajo en esos dos campos).
 */
public record AjusteDeAlarma(Boolean sonido, Boolean vibracion, Boolean voz, Boolean repetir) {

    private static final AjusteDeAlarma HEREDA = new AjusteDeAlarma(null, null, null, null);

    public AjusteDeAlarma {
        CombinacionDeAlarma.requireValida(sonido, vibracion, voz);
    }

    /** La capa que no dice nada: hereda los cuatro campos. */
    public static AjusteDeAlarma hereda() {
        return HEREDA;
    }

    /** La capa que fija los cuatro campos con esa combinacion. */
    public static AjusteDeAlarma de(CombinacionDeAlarma combinacion) {
        return new AjusteDeAlarma(combinacion.sonido(), combinacion.vibracion(), combinacion.voz(),
                combinacion.repetir());
    }

    /** {@code true} si no fija ningun campo. */
    public boolean esVacio() {
        return sonido == null && vibracion == null && voz == null && repetir == null;
    }

    /** La combinacion que resulta de poner esta capa sobre {@code base}, campo por campo. */
    public CombinacionDeAlarma aplicadoA(CombinacionDeAlarma base) {
        boolean conSonido = sonido != null ? sonido : base.sonido();
        boolean conVibracion = vibracion != null ? vibracion : base.vibracion();
        if (!conSonido && !conVibracion) {
            conSonido = base.sonido();
            conVibracion = base.vibracion();
        }
        boolean conVoz = (voz != null ? voz : base.voz()) && conSonido;
        return new CombinacionDeAlarma(conSonido, conVibracion, conVoz, repetir != null ? repetir : base.repetir());
    }
}
