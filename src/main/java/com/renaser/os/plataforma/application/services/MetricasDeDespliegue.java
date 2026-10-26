package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.MedirPedidoEnDespliegueUseCase;
import com.renaser.os.plataforma.application.ports.out.MetricasDeDesplieguePort;
import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.despliegue.ConteoDeDespliegue;
import com.renaser.os.plataforma.domain.model.despliegue.DespliegueDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.despliegue.FotoDeDespliegues;
import com.renaser.os.plataforma.domain.model.despliegue.MetricasPorHora.Conteo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las métricas del despliegue (spec 002, FR-020; contrato fase 2 §3.3): por funcionalidad en despliegue, hora y
 * dentro/fuera, cuántos pedidos y cuántos 5xx. Se cuenta en memoria y se escribe en lote cada minuto.
 *
 * <p><b>Contar no pierde nada aunque se escriba a la vez:</b> cada celda se suma con {@code merge} (atómico por clave) y
 * se toma con {@code remove} (también atómico): lo que llega después de tomarla abre una celda nueva y entra en el lote
 * siguiente. Si escribir falla, el lote vuelve a la memoria y sale en el próximo intento.
 *
 * <p>Se cuenta en cada nivel de la cadena que esté en despliegue: un pedido a {@code chat.grupos} con {@code chat} en
 * despliegue suma en {@code chat}; «dentro» es si la persona está dentro del despliegue de ESE nivel.
 */
@Service
public class MetricasDeDespliegue implements MedirPedidoEnDespliegueUseCase {

    private static final Logger log = LoggerFactory.getLogger(MetricasDeDespliegue.class);

    private final CopiaDeDespliegues copia;
    private final MetricasDeDesplieguePort port;
    private final Clock clock;
    private final Duration retencion;
    private final CatalogoDeFuncionalidades catalogo = CatalogoDeFuncionalidades.inicial();
    private final Map<Celda, Conteo> pendientes = new ConcurrentHashMap<>();

    public MetricasDeDespliegue(CopiaDeDespliegues copia, MetricasDeDesplieguePort port, Clock clock,
                                @Value("${renaser.plataforma.despliegue.metricas.retencion:PT48H}") Duration retencion) {
        this.copia = copia;
        this.port = port;
        this.clock = clock;
        this.retencion = retencion;
    }

    @Override
    public void registrar(String clave, UserId persona, boolean error) {
        FotoDeDespliegues foto = copia.foto();
        if (persona == null || clave == null || foto.sinDespliegues()) {
            return;
        }
        Instant hora = null;
        for (DefinicionDeFuncionalidad nivel : catalogo.cadena(clave)) {
            Optional<DespliegueDeFuncionalidad> despliegue = foto.de(nivel.clave());
            if (despliegue.isPresent()) {
                hora = hora != null ? hora : ConteoDeDespliegue.horaDe(clock.now());
                pendientes.merge(new Celda(nivel.clave(), hora, despliegue.get().incluye(persona)),
                        new Conteo(1, error ? 1 : 0), Conteo::mas);
            }
        }
    }

    /** Escribe lo contado desde la última vez, sumando a lo guardado. Devuelve cuántas celdas escribió. */
    public int escribirPendientes() {
        Map<Celda, Conteo> lote = tomarPendientes();
        if (lote.isEmpty()) {
            return 0;
        }
        try {
            port.sumar(aConteos(lote));
            return lote.size();
        } catch (RuntimeException e) {
            lote.forEach((celda, conteo) -> pendientes.merge(celda, conteo, Conteo::mas));
            log.warn("[plataforma] no se pudieron escribir {} metrica(s) del despliegue; se reintenta en el proximo lote",
                    lote.size(), e);
            return 0;
        }
    }

    /** Borra lo de más de 48 h (la consola muestra 24). */
    public int purgarViejas() {
        return port.borrarAnterioresA(ConteoDeDespliegue.horaDe(clock.now()).minus(retencion));
    }

    private Map<Celda, Conteo> tomarPendientes() {
        Map<Celda, Conteo> lote = new LinkedHashMap<>();
        for (Celda celda : List.copyOf(pendientes.keySet())) {
            Conteo conteo = pendientes.remove(celda);
            if (conteo != null) {
                lote.put(celda, conteo);
            }
        }
        return lote;
    }

    private static List<ConteoDeDespliegue> aConteos(Map<Celda, Conteo> lote) {
        List<ConteoDeDespliegue> conteos = new ArrayList<>(lote.size());
        lote.forEach((celda, conteo) -> conteos.add(new ConteoDeDespliegue(celda.clave(), celda.hora(),
                celda.dentro(), conteo.pedidos(), conteo.errores())));
        return conteos;
    }

    private record Celda(String clave, Instant hora, boolean dentro) {
    }
}
