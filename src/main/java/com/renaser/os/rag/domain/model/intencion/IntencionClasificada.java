package com.renaser.os.rag.domain.model.intencion;

import java.util.Optional;

/**
 * Lo que un clasificador rapido entendio de un mensaje, ANTES de que hable el modelo de lenguaje:
 * que quiere hacer la persona y, si nombro un habito, cual de los suyos de hoy.
 *
 * <p><b>Es una propuesta, no una orden.</b> Nada de esto autoriza ni ejecuta: el actor sigue
 * viniendo del JWT ({@code EjecutarHerramientaAgenteUseCase}), la herramienta sigue validando sus
 * guardas, y una escritura como {@code marcar_habito_completado} sigue necesitando que la persona
 * lo pida. Una similitud alta tampoco reemplaza esa confirmacion (plan de IA v2.1, §5.3).
 *
 * <p>Las intenciones son los nombres de {@code CatalogoHerramientasAgente} mas {@link #CONVERSAR},
 * para todo lo que no pide una herramienta.
 *
 * @param intencion vacio cuando no hay clasificador configurado ({@link #sinClasificar()})
 * @param habito    vacio si no hay habitos hoy o no hay clasificador; su etiqueta es el
 *                  {@code registroId}
 */
public record IntencionClasificada(Optional<Candidato> intencion, Optional<Candidato> habito) {

    /** El mensaje no pide ninguna herramienta: saluda, cuenta algo, pregunta sobre el programa. */
    public static final String CONVERSAR = "conversar";

    public IntencionClasificada {
        intencion = intencion == null ? Optional.empty() : intencion;
        habito = habito == null ? Optional.empty() : habito;
    }

    /** Lo que devuelve el adaptador {@code noop}: no hay opinion, el flujo sigue como siempre. */
    public static IntencionClasificada sinClasificar() {
        return new IntencionClasificada(Optional.empty(), Optional.empty());
    }
}
