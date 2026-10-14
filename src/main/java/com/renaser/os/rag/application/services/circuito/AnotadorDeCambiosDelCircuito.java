package com.renaser.os.rag.application.services.circuito;

import com.renaser.os.rag.api.CircuitoDeIaCambiadoEvent;
import com.renaser.os.rag.domain.model.circuito.CambioDelCircuito;
import com.renaser.os.rag.domain.model.circuito.CircuitoDeIa;
import com.renaser.os.shared.application.AuditoriaDePlataforma;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deja constancia de que un circuito se abrió o se cerró (spec 002, FR-016, SC-005): la fila {@code AUTOMATICO} en la
 * auditoría de {@code plataforma} y {@link CircuitoDeIaCambiadoEvent}, en la <b>misma transacción</b> (el outbox de
 * Modulith la necesita, y así no queda un aviso sin su fila ni al revés). Corta: no hay ninguna llamada a la IA adentro
 * (C-1).
 */
@Component
public class AnotadorDeCambiosDelCircuito {

    private final AuditoriaDePlataforma auditoria;
    private final ApplicationEventPublisher publisher;

    public AnotadorDeCambiosDelCircuito(AuditoriaDePlataforma auditoria, ApplicationEventPublisher publisher) {
        this.auditoria = auditoria;
        this.publisher = publisher;
    }

    @Transactional
    public void anotar(CambioDelCircuito cambio) {
        auditoria.registrarCambioAutomatico(cambio.parte().clave(), foto(cambio.antes()), foto(cambio.despues()),
                cambio.causa());
        publisher.publishEvent(new CircuitoDeIaCambiadoEvent(cambio.parte().clave(), cambio.despues().estado().name(),
                cambio.causa(), cambio.en()));
    }

    /** Lo que se ve en {@code antes}/{@code despues}: el estado y, abierto, las fechas (como texto ISO). */
    private static Map<String, Object> foto(CircuitoDeIa circuito) {
        Map<String, Object> foto = new LinkedHashMap<>();
        foto.put("estado", circuito.estado().name());
        if (circuito.abiertoDesde() != null) {
            foto.put("abiertoDesde", circuito.abiertoDesde().toString());
            foto.put("proximaPrueba", circuito.proximaPrueba().toString());
            foto.put("aperturasSeguidas", circuito.aperturasSeguidas());
        }
        return foto;
    }
}
