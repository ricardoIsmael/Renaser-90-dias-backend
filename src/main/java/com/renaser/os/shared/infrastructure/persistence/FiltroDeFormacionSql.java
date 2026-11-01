package com.renaser.os.shared.infrastructure.persistence;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;

import java.util.List;

/**
 * El único lugar que convierte un {@link AlcanceDeFormacion} en SQL (spec 022, D-295). Cada consulta que lista personas, grupos o contenido
 * agrega el predicado sobre la columna de formación que le toca ({@code pp.formacion}, {@code c.formacion}…) y liga la lista de códigos:
 *
 * <pre>
 *   // JdbcClient (parámetro con nombre)
 *   "… WHERE " + FiltroDeFormacionSql.predicado("c.formacion", "formaciones")
 *   jdbc.sql(sql).param("formaciones", FiltroDeFormacionSql.codigos(alcance))
 *
 *   // EntityManager nativo (parámetro posicional)
 *   "… WHERE " + FiltroDeFormacionSql.predicadoPosicional("c.formacion", 3)
 *   query.setParameter(3, FiltroDeFormacionSql.codigos(alcance))
 * </pre>
 *
 * Se usa una {@code List} y no un arreglo de SQL a propósito: Spring JDBC e Hibernate la expanden a {@code IN (?, ?)} sin trucos de tipos.
 * Con el alcance vacío la lista lleva {@link #NINGUNA}, un código que no es de ninguna formación: no pasa ninguna fila, y la consulta
 * conserva siempre su parámetro (no hay una rama especial que se pueda olvidar).
 */
public final class FiltroDeFormacionSql {

    /** El código que se liga cuando el alcance está vacío: ninguna formación lo tiene. */
    public static final String NINGUNA = "-";

    private FiltroDeFormacionSql() {
    }

    /** El predicado con un parámetro con nombre ({@code :nombre}). */
    public static String predicado(String columna, String parametro) {
        return columna + " IN (:" + parametro + ")";
    }

    /** El predicado con un parámetro posicional ({@code ?N}), para consultas nativas de {@code EntityManager}. */
    public static String predicadoPosicional(String columna, int posicion) {
        return columna + " IN (?" + posicion + ")";
    }

    /** El valor a ligar: los códigos del alcance (ordenados) o {@link #NINGUNA} si está vacío. */
    public static List<String> codigos(AlcanceDeFormacion alcance) {
        if (alcance.esVacio()) {
            return List.of(NINGUNA);
        }
        return alcance.formaciones().stream().map(Formacion::codigo).sorted().toList();
    }
}
