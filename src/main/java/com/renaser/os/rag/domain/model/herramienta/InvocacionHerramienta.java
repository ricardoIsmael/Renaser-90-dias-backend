package com.renaser.os.rag.domain.model.herramienta;

import java.util.Map;
import java.util.Objects;

/**
 * Lo que el modelo pidio ejecutar: el nombre de una herramienta y sus argumentos.
 *
 * <p>Los argumentos son {@code Map<String, String>} y no un tipo por herramienta a proposito: lo
 * que llega de un modelo es texto sin garantias, y fingir que ya esta tipado obligaria a que
 * cada capa vuelva a desconfiar. Se valida una vez, en el borde
 * ({@code DefinicionHerramienta.obligatoriosFaltantesEn} y el caso de uso), y de ahi para
 * adentro se trabaja con tipos de verdad.
 */
public record InvocacionHerramienta(String nombre, Map<String, String> argumentos) {

    public InvocacionHerramienta {
        Objects.requireNonNull(nombre, "nombre es obligatorio");
        argumentos = Map.copyOf(Objects.requireNonNull(argumentos, "argumentos es obligatorio"));
    }

    public static InvocacionHerramienta sinArgumentos(String nombre) {
        return new InvocacionHerramienta(nombre, Map.of());
    }

    /** {@code null} si el modelo no lo mando. */
    public String argumento(String nombreArgumento) {
        return argumentos.get(nombreArgumento);
    }
}
