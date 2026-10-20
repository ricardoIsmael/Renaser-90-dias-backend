package com.renaser.os.leadership.domain.model.cumplimiento;

import java.util.Collection;

/**
 * Cuántas personas hay en cada color del semáforo en la lista de un grupo: el «5 al día, 2 requieren atención» del
 * encabezado. Quien no se mide en el semáforo ({@link ColorDeLaFila#NO_SE_MIDE}) no entra en ninguno: el conteo es de
 * la misma gente que cuenta la tabla del mentor.
 */
public record ConteoDeLaLista(int verde, int amarillo, int rojo, int sinDatos) {

    public static ConteoDeLaLista de(Collection<PersonaDeLaLista> personas) {
        return new ConteoDeLaLista(cuantas(personas, ColorDeLaFila.VERDE), cuantas(personas, ColorDeLaFila.AMARILLO),
                cuantas(personas, ColorDeLaFila.ROJO), cuantas(personas, ColorDeLaFila.SIN_DATOS));
    }

    public int total() {
        return verde + amarillo + rojo + sinDatos;
    }

    private static int cuantas(Collection<PersonaDeLaLista> personas, ColorDeLaFila color) {
        return (int) personas.stream().filter(persona -> persona.color() == color).count();
    }
}
