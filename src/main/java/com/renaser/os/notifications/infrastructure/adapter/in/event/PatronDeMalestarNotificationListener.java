package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.rag.api.PatronDeMalestarRepetidoEvent;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Le pone en la bandeja a cada administrador que un aprendiz viene repitiendo expresiones de
 * malestar al escribirle al asistente.
 *
 * <p><b>Lo que el texto dice, y lo que tiene prohibido decir.</b> Dice que se repitio un patron que
 * conviene mirar. No dice que la persona este en crisis, ni deprimida, ni en riesgo: nada de eso se
 * evaluo, y afirmarlo seria un diagnostico escrito por un {@code contains} sobre una lista de
 * frases. Tampoco lleva una sola palabra de lo que la persona escribio — es dato personal
 * (CLAUDE.MD §5.4.9) — ni lo lleva el push, que sale con el mismo cuerpo.
 *
 * <p><b>Va a TODOS los administradores, no a uno</b>, por el mismo motivo que
 * {@code GrupoPorVencerNotificationListener}: no hay un "dueno" al que dirigirselo, y mandarselo al
 * primero de la lista deja el aviso dependiendo de quien este de vacaciones. ALCHEMIST entra junto a
 * ADMIN porque en este sistema es un administrador con mas permisos, no un rol aparte.
 *
 * <p>Que lo reciban varios no multiplica el aviso para nadie: el indice unico de
 * {@code notificaciones} es por (usuario, tipo, {@code origenEventoId}), asi que la clave del
 * episodio le entrega UNA a cada uno. Eso cubre ademas la reentrega del outbox de Modulith, que es
 * at-least-once, y que {@code rag} revise el patron en CADA mensaje posterior del mismo episodio.
 */
@Component
class PatronDeMalestarNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PatronDeMalestarNotificationListener.class);

    private static final Set<UserRole> ADMINISTRADORES = Set.of(UserRole.ADMIN, UserRole.ALCHEMIST);

    private static final String TITULO = "Un patron que conviene mirar";

    private final EmitirNotificacionUseCase emitirNotificacionUseCase;
    private final ParticipacionProgramaFinder participacionFinder;

    PatronDeMalestarNotificationListener(EmitirNotificacionUseCase emitirNotificacionUseCase,
                                          ParticipacionProgramaFinder participacionFinder) {
        this.emitirNotificacionUseCase = emitirNotificacionUseCase;
        this.participacionFinder = participacionFinder;
    }

    @ApplicationModuleListener
    void on(PatronDeMalestarRepetidoEvent event) {
        List<UserId> administradores = participacionFinder.usuariosActivosConRol(ADMINISTRADORES);
        if (administradores.isEmpty()) {
            /* Sin administradores activos no hay a quien avisarle, y eso NO es normal. Se registra
               en WARN para que aparezca —sin el nombre ni el id de la persona— en vez de terminar
               en silencio como si no hubiera pasado nada. */
            log.warn("[notifications.PatronDeMalestarNotificationListener] se repitio un patron de malestar "
                    + "y no hay ningun administrador activo a quien avisarle");
            return;
        }
        for (UserId administrador : administradores) {
            emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(
                    administrador, TipoNotificacion.PATRON_DE_MALESTAR_REPETIDO, TITULO, cuerpo(event),
                    event.rutaApp(), event.claveDeDeduplicacion()));
        }
    }

    /** "Al menos" porque la cuenta puede haber subido despues sin que este aviso se reescriba: la
     * clave del episodio es la misma y la deduplicacion —correctamente— no deja entrar otro. */
    private static String cuerpo(PatronDeMalestarRepetidoEvent event) {
        return event.nombreDeLaPersona() + " escribio al menos " + event.detecciones()
                + " veces en los ultimos " + event.diasDeLaVentana()
                + " dias expresiones de malestar al asistente. No es un diagnostico: es un patron que se repitio.";
    }
}
