package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.api.FuncionalidadCambiadaEvent;
import com.renaser.os.plataforma.application.ports.out.GuardarEstadoPort;
import com.renaser.os.plataforma.application.ports.out.RegistrarCambioAuditadoPort;
import com.renaser.os.plataforma.domain.model.auditoria.CambioAuditado;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.estado.CambioDeFuncionalidad;
import com.renaser.os.plataforma.domain.model.estado.EstadoDeFuncionalidad;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

/**
 * Lo que sigue a decidir un cambio de interruptor, en un solo lugar: guardar la fila con bloqueo optimista, dejar la
 * auditoría append-only y publicar {@link FuncionalidadCambiadaEvent} en la MISMA transacción (después del commit,
 * {@code AlCambiarFuncionalidad} recarga la copia de esta instancia y avisa a las demás por Redis). Lo usan la consola de
 * Soporte TI y el interruptor del Código Renaser en las fichas (spec 034): dos puertas, un solo camino de escritura, y por
 * eso un cambio de Administración queda auditado y rige igual que uno de la consola.
 *
 * <p>No es un bean: cada servicio lo arma con los puertos que ya tiene, para que sus pruebas y sus constructores sigan igual.
 * El cambio tiene que correr dentro de la transacción de quien lo llama.
 */
final class EscrituraDeEstado {

    private final GuardarEstadoPort guardarEstadoPort;
    private final RegistrarCambioAuditadoPort auditoria;
    private final ApplicationEventPublisher events;
    private final IdGenerator idGenerator;
    private final Clock clock;

    EscrituraDeEstado(GuardarEstadoPort guardarEstadoPort, RegistrarCambioAuditadoPort auditoria,
                      ApplicationEventPublisher events, IdGenerator idGenerator, Clock clock) {
        this.guardarEstadoPort = guardarEstadoPort;
        this.auditoria = auditoria;
        this.events = events;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * @param anterior la fila que estaba (para la auditoría), o {@code null} si nunca se había cambiado
     * @return el estado guardado, o vacío si otro cambio llegó antes y la fila ya no tenía la versión esperada: no se
     *         escribió ni se auditó nada
     */
    Optional<EstadoDeFuncionalidad> aplicar(DefinicionDeFuncionalidad definicion, EstadoDeFuncionalidad anterior,
                                            CambioDeFuncionalidad cambio, UserId actor, String dispositivo) {
        EstadoDeFuncionalidad nuevo = cambio.aplicar(guardarEstadoPort.siguienteVersion(), actor, clock.now());
        if (!guardarEstadoPort.guardar(nuevo, cambio.versionEsperada())) {
            return Optional.empty();
        }
        auditoria.registrar(CambioAuditado.manual(idGenerator.newId(), nuevo.actualizadoPor(), nuevo.clave(),
                antesParaAuditoria(definicion, anterior), nuevo.paraAuditoria(), cambio.motivo(), dispositivo,
                nuevo.actualizadoEn()));
        events.publishEvent(new FuncionalidadCambiadaEvent(nuevo.clave(), definicion.critica(),
                !nuevo.prendidaGlobal(), nuevo.actualizadoPor(), cambio.motivo(), nuevo.actualizadoEn(),
                definicion.nombre(), cambio.resumen()));
        return Optional.of(nuevo);
    }

    private static java.util.Map<String, Object> antesParaAuditoria(DefinicionDeFuncionalidad definicion,
                                                                    EstadoDeFuncionalidad anterior) {
        return anterior != null ? anterior.paraAuditoria() : EstadoDeFuncionalidad.porDefectoParaAuditoria(definicion);
    }
}
