package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.estado.ActorEvaluado;
import com.renaser.os.plataforma.domain.model.estado.EvaluacionDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.estado.ReglaDeDisponibilidad;
import com.renaser.os.plataforma.domain.model.estado.ResultadoDeEvaluacion;
import com.renaser.os.shared.application.EstadoAutomaticoDeFuncionalidades;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * La evaluación del contrato §5 sobre la copia en memoria y los apagados automáticos que publique {@code rag}. Es la
 * única puerta: el interceptor, los procesos, {@code /funcionalidades/mias} y la consola preguntan acá.
 */
@Component
class EstadoVigente {

    private static final Logger log = LoggerFactory.getLogger(EstadoVigente.class);

    private final CopiaDeEstados copia;
    private final ObjectProvider<EstadoAutomaticoDeFuncionalidades> proveedorDeAutomaticos;
    private volatile List<EstadoAutomaticoDeFuncionalidades> automaticos;
    private final ObjectProvider<ReglaDeDisponibilidad> proveedorDeReglas;
    private volatile List<ReglaDeDisponibilidad> reglas;
    private final CatalogoDeFuncionalidades catalogo = CatalogoDeFuncionalidades.inicial();
    private final EvaluacionDeFuncionalidades evaluacion = new EvaluacionDeFuncionalidades(catalogo);

    /**
     * Puede no haber ningún estado automático (sin {@code rag} o antes del circuito) ni ninguna regla de disponibilidad
     * (antes de la fase 2): por eso los proveedores.
     */
    EstadoVigente(CopiaDeEstados copia, ObjectProvider<EstadoAutomaticoDeFuncionalidades> proveedorDeAutomaticos,
                  ObjectProvider<ReglaDeDisponibilidad> proveedorDeReglas) {
        this.copia = copia;
        this.proveedorDeAutomaticos = proveedorDeAutomaticos;
        this.proveedorDeReglas = proveedorDeReglas;
    }

    /** Los estados automáticos registrados; se resuelven una vez, en el primer pedido. */
    List<EstadoAutomaticoDeFuncionalidades> automaticos() {
        List<EstadoAutomaticoDeFuncionalidades> actuales = automaticos;
        if (actuales == null) {
            actuales = proveedorDeAutomaticos.orderedStream().toList();
            automaticos = actuales;
        }
        return actuales;
    }

    /**
     * Las reglas de disponibilidad registradas (fase 2), en su {@code @Order}; se resuelven una vez, en el primer pedido.
     * Cada una queda envuelta: una regla que falla no puede tirar el pedido, se trata como «le corresponde» y se anota.
     */
    List<ReglaDeDisponibilidad> reglas() {
        List<ReglaDeDisponibilidad> actuales = reglas;
        if (actuales == null) {
            actuales = proveedorDeReglas.orderedStream().map(EstadoVigente::aPruebaDeFallas).toList();
            reglas = actuales;
        }
        return actuales;
    }

    private static ReglaDeDisponibilidad aPruebaDeFallas(ReglaDeDisponibilidad regla) {
        return (cadena, actor) -> {
            try {
                return regla.noDisponibleEn(cadena, actor);
            } catch (RuntimeException e) {
                log.warn("[plataforma] la regla de disponibilidad {} fallo con {}; se considera disponible",
                        regla.getClass().getSimpleName(), cadena, e);
                return Optional.empty();
            }
        };
    }

    CatalogoDeFuncionalidades catalogo() {
        return catalogo;
    }

    /**
     * La versión que ve la app: la del estado manual más la del automático (el circuito de la IA). Las dos solo crecen,
     * así que la suma también; y cambia cuando el circuito abre o cierra (contrato §6, coordinación con la app).
     */
    long version() {
        long version = copia.foto().version();
        for (EstadoAutomaticoDeFuncionalidades automatico : automaticos()) {
            try {
                version += Math.max(0L, automatico.version());
            } catch (RuntimeException e) {
                log.warn("[plataforma] no se pudo leer la version del estado automatico", e);
            }
        }
        return version;
    }

    /** @param actor {@code null} para la evaluación global de un proceso sin actor (sin reglas de disponibilidad) */
    ResultadoDeEvaluacion evaluar(String clave, ActorEvaluado actor) {
        return evaluacion.evaluar(clave, copia.foto(), actor, this::apagadaAutomaticamente,
                actor == null ? List.of() : reglas());
    }

    /** Un fallo del circuito no puede tirar el pedido: se trata como «no apagada» y se anota. */
    private Optional<String> apagadaAutomaticamente(String clave) {
        for (EstadoAutomaticoDeFuncionalidades automatico : automaticos()) {
            try {
                Optional<String> aviso = automatico.apagadaAutomaticamente(clave);
                if (aviso.isPresent()) {
                    return aviso;
                }
            } catch (RuntimeException e) {
                log.warn("[plataforma] el estado automatico de {} fallo; se considera prendida", clave, e);
            }
        }
        return Optional.empty();
    }
}
