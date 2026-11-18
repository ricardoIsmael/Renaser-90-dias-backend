package com.renaser.os.leadership.domain.model.mentor;

/**
 * Cuántos aprendices de un mentor hay en cada color del semáforo (spec 056, FR-003). Son las mismas cuentas del semáforo de sus
 * aprendices, con los nombres que la app del líder dibuja: rojo, ámbar, verde y sin datos. Un aprendiz sin datos no se cuenta como verde
 * ni como rojo.
 */
public record AprendicesPorColor(int rojo, int ambar, int verde, int sinDatos) {

    public AprendicesPorColor {
        if (rojo < 0 || ambar < 0 || verde < 0 || sinDatos < 0) {
            throw new IllegalArgumentException("Una cantidad de aprendices no puede ser negativa");
        }
    }

    /** El mentor que no lidera ningún grupo: nadie en ningún color. */
    public static AprendicesPorColor ninguno() {
        return new AprendicesPorColor(0, 0, 0, 0);
    }

    public int total() {
        return rojo + ambar + verde + sinDatos;
    }
}
