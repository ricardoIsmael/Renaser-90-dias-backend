package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Leer Espiritu, Santuario o Dia sin celular con la traduccion de errores de siempre: una
 * herramienta no lanza, devuelve un {@code Fallo} legible (contrato de {@link HerramientaAgente}).
 * Compartido por las cuatro herramientas de foco del dia para que digan lo mismo. Mismo patron que
 * {@link LecturaDelPlan}.
 */
final class LecturaDelEnfoque {

    private static final Logger log = LoggerFactory.getLogger(LecturaDelEnfoque.class);

    private LecturaDelEnfoque() {
    }

    /** @param queSeLee "su Espiritu de hoy", "su Santuario"...: lo que se nombra si la lectura falla */
    static <T> ResultadoHerramienta con(Supplier<T> lectura, String queSeLee,
                                        Function<T, ResultadoHerramienta> siguiente) {
        T leido;
        try {
            leido = lectura.get();
        } catch (NoSuchElementException sinPrograma) {
            return ResultadoHerramienta.fallo("No encontre un programa activo para esta cuenta.");
        } catch (NotAuthorizedException sinAcceso) {
            return ResultadoHerramienta.fallo("La cuenta esta suspendida o todavia no cursa el programa: no puedo "
                    + "consultar " + queSeLee + ".");
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo leer {}", queSeLee, falla);
            return ResultadoHerramienta.fallo("No pude consultar " + queSeLee + " en este momento.");
        }
        return siguiente.apply(leido);
    }
}
