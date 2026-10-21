package com.renaser.os.points.domain.model.semaforo.mensaje;

import java.util.Collection;

/**
 * Lo que se sabe de la persona para escribir su mensaje de hoy. Todo es opcional ({@code null} = no se sabe): un
 * mensaje que pide un dato que falta no es candidato (FR-005), así nunca queda un hueco ni una llave a la vista.
 *
 * <p>Un texto vacío, un día o un {@code faltan} menores que 1 se tratan como «no se sabe»: «Te faltan 0» o «día 0»
 * serían mentiras.
 *
 * @param nombre nombre de la persona; al escribir el mensaje se usa solo el primero
 * @param dia    día del programa en que está, desde 1
 * @param fase   nombre de la fase tal como lo muestra la app
 * @param faltan cuántos pendientes de hoy le faltan para subir al color siguiente
 */
public record DatosDelMensaje(String nombre, Integer dia, String fase, String habitoFlojo, String habitoFuerte,
                              Integer faltan) {

    public DatosDelMensaje {
        nombre = texto(nombre);
        fase = texto(fase);
        habitoFlojo = texto(habitoFlojo);
        habitoFuerte = texto(habitoFuerte);
        dia = dia != null && dia >= 1 ? dia : null;
        faltan = faltan != null && faltan >= 1 ? faltan : null;
    }

    public static DatosDelMensaje sinDatos() {
        return new DatosDelMensaje(null, null, null, null, null, null);
    }

    public boolean tiene(Variable variable) {
        return valorDe(variable) != null;
    }

    public boolean tieneTodas(Collection<Variable> variables) {
        return variables.stream().allMatch(this::tiene);
    }

    /** El valor listo para escribir en el mensaje, o {@code null} si no se sabe. */
    public String valorDe(Variable variable) {
        return switch (variable) {
            case NOMBRE -> primerNombre();
            case DIA -> dia == null ? null : dia.toString();
            case FASE -> fase;
            case HABITO_FLOJO -> habitoFlojo;
            case HABITO_FUERTE -> habitoFuerte;
            case FALTAN -> faltan == null ? null : faltan.toString();
        };
    }

    private String primerNombre() {
        return nombre == null ? null : nombre.split("\\s+")[0];
    }

    private static String texto(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
