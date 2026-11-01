package com.renaser.os.users.api;

import com.renaser.os.shared.domain.DuracionDelPrograma;
import com.renaser.os.shared.domain.Formacion;

import java.util.List;

/**
 * Lo que Administración configura de una formación (tabla {@code formaciones}, V125, spec 022): nombre, descripción, si se ofrece y las opciones de
 * duración. {@code version} es el control de versión optimista de la edición.
 */
public record FormacionConfigurada(Formacion formacion, String nombre, String descripcion, boolean activa,
                                   List<Integer> duracionesDias, int version) {

    public FormacionConfigurada {
        duracionesDias = List.copyOf(duracionesDias);
    }

    /** «3 meses», «1 año», «90 días»: con una sola opción se dice en días (es un dato, no una elección); con varias, como las dijo el dueño. */
    public List<String> etiquetasDeDuracion() {
        return duracionesDias.stream()
                .map(dias -> duracionesDias.size() == 1 ? dias + " días" : DuracionDelPrograma.etiqueta(dias))
                .toList();
    }

    /** La duración de las personas que se acepten sin elegirla: la única opción, o la primera (la más corta). */
    public int duracionPorDefecto() {
        return duracionesDias.stream().sorted().findFirst().orElse(DuracionDelPrograma.POR_DEFECTO);
    }

    public boolean ofrece(int dias) {
        return duracionesDias.contains(dias);
    }
}
