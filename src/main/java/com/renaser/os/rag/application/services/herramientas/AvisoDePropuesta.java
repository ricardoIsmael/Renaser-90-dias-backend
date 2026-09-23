package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;

/**
 * Lo que lee el modelo despues de dejar una propuesta de las herramientas del plan de habitos
 * (fase 2, D-153). Mismo mensaje que {@link PropuestaDeMarcarHabito}: la accion NO esta hecha, y
 * el modelo no puede decir lo contrario.
 */
final class AvisoDePropuesta {

    private AvisoDePropuesta() {
    }

    static ResultadoHerramienta creada(String resumen, String advertencia) {
        return ResultadoHerramienta.exito("Propuesta creada: " + resumen + ". TODAVIA NO esta hecho: la persona "
                + "tiene que tocar Confirmar en la app para que se aplique. No digas que ya quedo hecho; dile que "
                + "confirme con el boton." + (advertencia == null ? "" : " " + advertencia));
    }

    static ResultadoHerramienta noSePudoPreparar() {
        return ResultadoHerramienta.fallo("No pude preparar la confirmacion en este momento.");
    }
}
