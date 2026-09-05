package com.renaser.os.rag.domain.model.herramienta;

import java.util.Objects;

/**
 * Un argumento de una {@link DefinicionHerramienta}.
 *
 * <p>{@code tipo} es deliberadamente pobre — no es un JSON Schema. Las herramientas de este
 * dominio reciben identificadores y poco mas, y un esquema completo seria adivinar hoy la forma
 * que va a necesitar el proveedor de manana. El adaptador que traduzca al formato real (Spring
 * AI, MCP, lo que sea) enriquece desde aca; al reves — nacer con el esquema de un proveedor
 * concreto en el dominio — es lo que hace falta desarmar despues.
 */
public record ParametroHerramienta(String nombre, TipoParametroHerramienta tipo, String descripcion,
                                    boolean obligatorio) {

    public ParametroHerramienta {
        Objects.requireNonNull(nombre, "nombre es obligatorio");
        Objects.requireNonNull(tipo, "tipo es obligatorio");
        Objects.requireNonNull(descripcion, "descripcion es obligatoria");
    }

    public static ParametroHerramienta obligatorio(String nombre, TipoParametroHerramienta tipo, String descripcion) {
        return new ParametroHerramienta(nombre, tipo, descripcion, true);
    }
}
