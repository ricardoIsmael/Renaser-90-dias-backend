package com.renaser.os.rag.domain.model.herramienta;

import java.util.List;
import java.util.Objects;

/**
 * Que puede HACER el agente, descrito de una forma que un modelo pueda entender: un nombre
 * estable, una frase que explica cuando conviene usarla, y sus parametros.
 *
 * <p><b>Por que esto vive en el dominio y no en el adaptador del proveedor.</b> Que el
 * acompanante de los 90 dias pueda mirar los habitos de hoy, decir cuantos puntos hay en juego y
 * marcar uno como hecho es una decision de producto, no un detalle de Gemini. Descrita aca, el
 * dia que se enchufe un modelo real el trabajo es traducir estas definiciones al formato de ese
 * proveedor (en Spring AI, un {@code ToolCallback}) — no volver a decidir que herramientas hay ni
 * reescribir su ejecucion. Ese es exactamente el objetivo del encargo: enchufar el modelo tiene
 * que ser configuracion, no reescritura.
 *
 * <p>La descripcion no es decorativa: es lo unico que el modelo lee para decidir si llamar a la
 * herramienta. Se escribe pensando en eso — cuando SI y cuando NO usarla.
 *
 * @param nombre      identificador estable, en {@code snake_case}. Es lo que el modelo emite;
 *                    cambiarlo rompe cualquier conversacion a mitad de camino
 * @param descripcion para que sirve y cuando conviene llamarla, en castellano
 * @param parametros  en orden; puede estar vacio
 */
public record DefinicionHerramienta(String nombre, String descripcion, List<ParametroHerramienta> parametros) {

    public DefinicionHerramienta {
        Objects.requireNonNull(nombre, "nombre es obligatorio");
        Objects.requireNonNull(descripcion, "descripcion es obligatoria");
        if (nombre.isBlank() || descripcion.isBlank()) {
            throw new IllegalArgumentException("nombre y descripcion de la herramienta son obligatorios");
        }
        parametros = List.copyOf(Objects.requireNonNull(parametros, "parametros es obligatorio"));
    }

    /** Sin argumentos: la herramienta se resuelve entera con la identidad de quien pregunta. */
    public static DefinicionHerramienta sinParametros(String nombre, String descripcion) {
        return new DefinicionHerramienta(nombre, descripcion, List.of());
    }

    /**
     * Los nombres de los parametros obligatorios que faltan en una invocacion. Vacio si la
     * invocacion esta completa. Se valida en el dominio y no en cada adaptador porque un modelo
     * omite argumentos con total naturalidad: es el caso comun, no el borde.
     */
    public List<String> obligatoriosFaltantesEn(InvocacionHerramienta invocacion) {
        return parametros.stream()
                .filter(ParametroHerramienta::obligatorio)
                .map(ParametroHerramienta::nombre)
                .filter(nombreParametro -> esVacio(invocacion.argumento(nombreParametro)))
                .toList();
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
