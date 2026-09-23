package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * Una lectura de {@code academy} dicha en el idioma de las herramientas (2026-09-23): la corre y,
 * si {@code academy} la rechaza, devuelve un {@code Fallo} apto para mostrar. Una sola traduccion
 * para {@code consultar_clase_de_hoy}, {@code proponer_entregar_clase_de_hoy},
 * {@code consultar_mis_cursos} y {@code consultar_por_que_esta_bloqueado}.
 *
 * <p>Se traduce por TIPO de excepcion, el contrato documentado de los casos de uso de
 * {@code academy} ({@code requireProgreso}: {@code NoSuchElementException} si no hay fila,
 * {@code NotAuthorizedException} si esta suspendida o no sigue el programa). El mensaje crudo va
 * al log, nunca al modelo.
 */
final class LecturaDeAcademia {

    private static final Logger log = LoggerFactory.getLogger(LecturaDeAcademia.class);

    private LecturaDeAcademia() {
    }

    static ResultadoHerramienta consultar(String herramienta, Supplier<ResultadoHerramienta> consulta) {
        try {
            return consulta.get();
        } catch (NoSuchElementException sinPrograma) {
            return ResultadoHerramienta.fallo("No encontre un programa activo para esta cuenta.");
        } catch (NotAuthorizedException sinAcceso) {
            return ResultadoHerramienta.fallo("La cuenta esta suspendida o no sigue el programa de 90 dias: no "
                    + "puedo consultar su academia.");
        } catch (RuntimeException falla) {
            log.warn("[rag] la herramienta {} no pudo leer de academy", herramienta, falla);
            return ResultadoHerramienta.fallo("No pude consultar la academia en este momento.");
        }
    }
}
