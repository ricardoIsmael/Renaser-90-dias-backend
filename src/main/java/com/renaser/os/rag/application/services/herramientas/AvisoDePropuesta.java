package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;

/**
 * Lo que lee el modelo despues de dejar una propuesta de las herramientas del plan de habitos
 * (fase 2, D-153). Mismo mensaje que {@link PropuestaDeMarcarHabito}: la accion NO esta hecha, y
 * el modelo no puede decir lo contrario.
 */
final class AvisoDePropuesta {

    /**
     * Antes decia "dile que confirme con el boton" y el modelo lo repetia en cada propuesta, con
     * negritas y el contenido otra vez ("Toca el boton **Confirmar** en la app para..."), cuando la
     * persona ya ve la tarjeta con sus botones (bateria del 2026-09-25).
     */
    static final String COMO_DECIRLO = "TODAVIA NO esta hecho: se aplica solo si la persona toca Confirmar en la app, donde ya "
                + "ve la propuesta con su detalle y sus botones. No digas que ya quedo hecho ni repitas lo que "
                + "propone: basta una frase corta, por ejemplo \"Te deje la propuesta abajo para que la "
                + "confirmes\".";

    private AvisoDePropuesta() {
    }

    static ResultadoHerramienta creada(String resumen, String advertencia) {
        return ResultadoHerramienta.exito("Propuesta creada: " + resumen + ". " + COMO_DECIRLO
                + (advertencia == null ? "" : " " + advertencia));
    }

    static ResultadoHerramienta noSePudoPreparar() {
        return ResultadoHerramienta.fallo("No pude preparar la confirmacion en este momento.");
    }
}
