package com.renaser.os.habits.infrastructure.adapter.in.event;

import com.renaser.os.community.api.PublicacionCreadaEvent;
import com.renaser.os.habits.application.ports.in.registro.CerrarPostDiarioComunidadUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Publicar en el Muro cierra el habito de post diario en comunidad y paga sus puntos (E-121).
 *
 * <p>Primer oyente de eventos de {@code habits}: hasta ahora los efectos secundarios entre
 * modulos que terminaban en un habito llegaban por llamada directa a un puerto de
 * {@code habits.api}. Aca no sirve, porque el efecto no lo pide {@code community} — lo necesita
 * {@code habits}, y {@code community} no tiene por que enterarse de que existe un habito atado a
 * publicar. El evento invierte esa dependencia: {@code community} publica un hecho y quien lo
 * necesita se cuelga.
 *
 * <p><b>Por que colgado del evento y no del endpoint de publicar.</b> Porque hay dos vias de
 * publicacion: la manual del Muro y la automatica que {@code rocks} dispara al completar la roca
 * diaria ({@code publicarDesdeEvidencia}). El dueno del producto confirmo el 2026-09-05 que la
 * segunda tambien debe cerrar el habito; las dos emiten este mismo evento, asi que colgarse de el
 * cubre las dos sin que ninguna de las dos lo sepa.
 *
 * <p>{@code @ApplicationModuleListener} (y no {@code @EventListener} a secas) es lo que mete al
 * evento en el outbox de Modulith: corre async, en su propia transaccion, DESPUES del commit de
 * la que creo la publicacion. Que sea despues del commit importa: la politica que valida el
 * cierre consulta {@code publicaciones_muro}, y necesita ver la fila ya escrita.
 */
@Component
class PublicacionCreadaHabitoListener {

    private static final Logger log = LoggerFactory.getLogger(PublicacionCreadaHabitoListener.class);

    private final CerrarPostDiarioComunidadUseCase cerrarPostDiarioUseCase;

    PublicacionCreadaHabitoListener(CerrarPostDiarioComunidadUseCase cerrarPostDiarioUseCase) {
        this.cerrarPostDiarioUseCase = cerrarPostDiarioUseCase;
    }

    /**
     * {@code occurredAt} y no el reloj de ahora: si el proceso se reinicia y Modulith reentrega
     * este evento al dia siguiente, el habito que hay que cerrar sigue siendo el del dia en que la
     * persona publico (ver el javadoc del caso de uso).
     *
     * <p><b>Se atrapa todo fallo y no se propaga ninguno.</b> La publicacion ya esta guardada y es
     * lo que la persona vino a hacer; que ademas se le cierre un habito es un efecto secundario, y
     * un efecto secundario no puede dejar el evento dando vueltas en el outbox para siempre. Cada
     * camino de falla es seguro de descartar: el doble pago lo impiden el estado terminal y el
     * bloqueo pesimista de {@code RegistroService}, no este {@code catch}. Si el habito quedo sin
     * cerrar, el aprendiz todavia puede cerrarlo a mano desde Training — {@code PoliticaPostDiarioComunidad}
     * lo va a dejar pasar, porque la publicacion existe.
     */
    @ApplicationModuleListener
    void on(PublicacionCreadaEvent evento) {
        try {
            cerrarPostDiarioUseCase.alPublicarEnElMuro(evento.autorId(), evento.occurredAt());
        } catch (RuntimeException fallaDelNegocio) {
            log.warn("[habits] la publicacion {} no pudo cerrar el habito de post diario: {}",
                    evento.publicacionId(), fallaDelNegocio.toString());
        }
    }
}
