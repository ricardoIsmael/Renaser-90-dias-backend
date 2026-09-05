package com.renaser.os.habits.application.ports.in.espiritu;

import com.renaser.os.shared.domain.UserId;

import java.util.Optional;
import java.util.UUID;

/**
 * Cierra el habito de catalogo {@code PASTILLA_RENACER} para HOY, con el resumen del audio
 * como respuesta de texto.
 *
 * <p><b>Que resuelve.</b> Es el "espejo hacia Pastilla Renacer" que estaba anotado como
 * pregunta abierta desde la primera pasada de Espiritu (docs/MODULO_HABITS.md §10.1 y §10.4,
 * y el javadoc de {@code EspirituService}): en el backend viejo, entregar el resumen del
 * audio del dia completaba ademas el habito ({@code completePastillaRenacerTrack},
 * spirit-audio/service.ts, best-effort/no-fatal). Sin esto, el aprendiz manda su resumen y el
 * habito sigue apareciendo pendiente en Training — que es justo lo que el dueno del producto
 * pidio arreglar ("despues de contestar debe salir registrado o completado").
 *
 * <p><b>Por que un puerto propio y no un "completar cualquier habito por clave".</b> Mismo
 * criterio, literal, que {@code CompletarClaseDiariaHabitoUseCase}: un generico abriria para
 * cualquier llamador un atajo para completar habitos ajenos. Este puerto solo sabe hacer una
 * cosa.
 *
 * <p><b>Por que devuelve {@code Optional} y no lanza.</b> El espejo es un efecto secundario
 * de la entrega, no su condicion: si el habito esta pausado, desactivado, o el track de hoy
 * todavia no se genero, la entrega del resumen igual vale y se guarda. El llamador
 * ({@code EspirituService}) trata el vacio como "no habia nada que reflejar", no como error.
 */
public interface CompletarPastillaRenacerUseCase {

    /** Identidad funcional estable del habito de catalogo (tabla {@code habitos}, columna {@code clave_sistema}). */
    String CLAVE_SISTEMA_PASTILLA_RENACER = "PASTILLA_RENACER";

    /**
     * Idempotente: si el registro de hoy ya esta COMPLETADO devuelve el resultado ya otorgado,
     * sin volver a completarlo ni a sumar puntos.
     *
     * @return vacio cuando no hay nada que reflejar (el habito no esta en el catalogo, o el
     *         participante no tiene track de ese habito para hoy)
     */
    Optional<RegistroCompletado> completarDeHoy(UserId participanteId, String resumen);

    record RegistroCompletado(UUID registroId, int puntosOtorgados) {
    }
}
