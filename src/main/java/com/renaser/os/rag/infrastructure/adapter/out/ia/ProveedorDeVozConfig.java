package com.renaser.os.rag.infrastructure.adapter.out.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * Falla al arrancar, con un mensaje que se entiende, si {@code renaser.ia.voz.proveedor} tiene un
 * valor que ningun adaptador de voz reconoce (E-228).
 *
 * <p>Sin esto, un valor desconocido dejaba a Spring sin ningun {@code SintetizarVozPort} y el
 * backend moria con "required a bean of type SintetizarVozPort that could not be found", que no dice
 * que el problema es una variable de entorno.
 */
@Configuration
class ProveedorDeVozConfig {

    static final Set<String> PROVEEDORES = Set.of("noop", "piper", "google");

    ProveedorDeVozConfig(@Value("${renaser.ia.voz.proveedor:noop}") String proveedor) {
        validar(proveedor);
    }

    static void validar(String proveedor) {
        if (!PROVEEDORES.contains(proveedor)) {
            throw new IllegalStateException("renaser.ia.voz.proveedor (IA_VOZ_PROVEEDOR) = '" + proveedor
                    + "' no existe. Valores validos: noop (sin voz del servidor), piper, google (voz Kore de Gemini).");
        }
    }
}
