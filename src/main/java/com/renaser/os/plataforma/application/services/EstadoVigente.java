package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.estado.EvaluacionDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.estado.ResultadoDeEvaluacion;
import com.renaser.os.shared.application.EstadoAutomaticoDeFuncionalidades;
import com.renaser.os.users.api.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

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
    private final CatalogoDeFuncionalidades catalogo = CatalogoDeFuncionalidades.inicial();
    private final EvaluacionDeFuncionalidades evaluacion = new EvaluacionDeFuncionalidades(catalogo);

    /** Puede no haber ningún estado automático (sin {@code rag} o antes del circuito): por eso el proveedor. */
    EstadoVigente(CopiaDeEstados copia, ObjectProvider<EstadoAutomaticoDeFuncionalidades> proveedorDeAutomaticos) {
        this.copia = copia;
        this.proveedorDeAutomaticos = proveedorDeAutomaticos;
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

    /** @param rol {@code null} para la evaluación global de un proceso sin actor */
    ResultadoDeEvaluacion evaluar(String clave, Supplier<UserRole> rol) {
        return evaluacion.evaluar(clave, copia.foto(), rol, this::apagadaAutomaticamente);
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
