package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.api.FuncionalidadCambiadaEvent;
import com.renaser.os.plataforma.application.ports.out.RegistrarCambioAuditadoPort;
import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.despliegue.DespliegueCambiado;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Lo que deja un cambio de despliegue, en la MISMA transacción que el cambio (contrato fase 2 §3.4): la fila MANUAL de
 * la auditoría y los eventos. El escalón (empezar, cambiar, terminar) avisa por push como un interruptor de la fase 1;
 * sumar o quitar una probadora, no. Los dos recargan la copia después del commit ({@link DespliegueCambiado}).
 */
@Component
class AnotadorDeDespliegues {

    private final RegistrarCambioAuditadoPort auditoria;
    private final ApplicationEventPublisher events;
    private final IdGenerator idGenerator;

    AnotadorDeDespliegues(RegistrarCambioAuditadoPort auditoria, ApplicationEventPublisher events,
                          IdGenerator idGenerator) {
        this.auditoria = auditoria;
        this.events = events;
        this.idGenerator = idGenerator;
    }

    /** @param resumen el escalón nuevo en palabras, para el push («En despliegue: 5 % → 25 %») */
    void escalon(DefinicionDeFuncionalidad definicion, Anotacion anotacion, String resumen) {
        registrar(anotacion);
        events.publishEvent(new FuncionalidadCambiadaEvent(definicion.clave(), definicion.critica(), false,
                anotacion.actor(), anotacion.motivo(), anotacion.en(), definicion.nombre(), resumen));
        events.publishEvent(new DespliegueCambiado(anotacion.clave(), anotacion.en()));
    }

    void probadoras(Anotacion anotacion) {
        registrar(anotacion);
        events.publishEvent(new DespliegueCambiado(anotacion.clave(), anotacion.en()));
    }

    private void registrar(Anotacion anotacion) {
        auditoria.registrar(CambioAuditado.manual(idGenerator.newId(), anotacion.actor(), anotacion.clave(),
                anotacion.antes(), anotacion.despues(), anotacion.motivo(), anotacion.dispositivo(), anotacion.en()));
    }

    /** @param antes y @param despues {@code {"despliegue": {...}}} o {@code {"despliegue": null}} */
    record Anotacion(UserId actor, String clave, Map<String, Object> antes, Map<String, Object> despues,
                     String motivo, String dispositivo, Instant en) {
    }
}
