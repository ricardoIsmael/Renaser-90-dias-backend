package com.renaser.os.rag.application.services;

import com.renaser.os.rag.api.PatronDeMalestarRepetidoEvent;
import com.renaser.os.rag.application.ports.in.seguridad.RevisarPatronDeMalestarUseCase;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.seguridad.ExpresionesDeMalestar;
import com.renaser.os.rag.domain.model.seguridad.MensajeDeApoyo;
import com.renaser.os.rag.domain.model.seguridad.PatronDeMalestarRepetido;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Las dos respuestas a que alguien haya repetido expresiones de malestar: ofrecerle a la persona un
 * recurso de ayuda real, y avisarle a quien pueda actuar.
 *
 * <p><b>Ninguna de las dos afirma nada sobre nadie.</b> El texto para la persona lo escribe el
 * dueno en configuracion ({@code MensajeDeApoyo}); el aviso dice que se repitio un patron que
 * conviene mirar y nada mas. Este servicio no clasifica, no diagnostica y no llama a ninguna IA —
 * es comparacion de texto contra una lista y una cuenta sobre fechas.
 *
 * <p><b>La cuenta se deriva, no se acumula (regla 02 §2).</b> No hay contador ni tabla nueva: los
 * mensajes con su texto y su fecha ya estan en {@code mensajes_renasia}. Correr esto dos veces da
 * lo mismo, y una caida del backend no deja a nadie con la cuenta corrida.
 *
 * <p><b>Por que se mira primero el mensaje en memoria.</b> La cuenta de la ventana solo puede SUBIR
 * cuando entra un mensaje que cuenta; sin uno nuevo, la ventana desliza y la cuenta baja. Asi que si
 * el mensaje recien escrito no contiene ninguna expresion, el umbral no puede haberse cruzado ahora
 * y no hace falta consultar nada. El caso normal —la enorme mayoria de los mensajes— no toca la base.
 *
 * <p><b>{@code @Transactional} corto, y antes de la IA.</b> Lo pide el outbox de Spring Modulith: un
 * {@code @ApplicationModuleListener} se entrega despues del commit, asi que un evento publicado sin
 * transaccion se perderia. La transaccion abarca solo la lectura y la publicacion —Hibernate ni
 * siquiera toma una conexion cuando no hay consulta que hacer— y {@code ConversacionRenasiaService}
 * llama a este caso de uso ANTES de hablar con el modelo, nunca durante: ningun puerto de IA corre
 * dentro de esta transaccion (regla 01, C-1).
 */
@Service
public class PatronDeMalestarService implements RevisarPatronDeMalestarUseCase {

    private static final Logger log = LoggerFactory.getLogger(PatronDeMalestarService.class);

    /** Cuando no se puede resolver el nombre. El aviso sirve igual: la ruta lleva a la ficha. */
    private static final String SIN_NOMBRE = "Un alumno";

    private final LoadMensajeRenasiaPort loadMensajeRenasiaPort;
    private final UserSummaryFinder userSummaryFinder;
    private final ApplicationEventPublisher publisher;
    private final MensajeDeApoyo mensajeDeApoyo;
    private final Clock clock;

    public PatronDeMalestarService(LoadMensajeRenasiaPort loadMensajeRenasiaPort,
                                    UserSummaryFinder userSummaryFinder, ApplicationEventPublisher publisher,
                                    MensajeDeApoyo mensajeDeApoyo, Clock clock) {
        this.loadMensajeRenasiaPort = loadMensajeRenasiaPort;
        this.userSummaryFinder = userSummaryFinder;
        this.publisher = publisher;
        this.mensajeDeApoyo = mensajeDeApoyo;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<String> revisar(UserId actorId, String textoDelMensaje) {
        if (!ExpresionesDeMalestar.apareceEn(textoDelMensaje)) {
            return Optional.empty();
        }
        Instant ahora = clock.now();
        Optional<PatronDeMalestarRepetido> patron =
                PatronDeMalestarRepetido.enLaVentana(actorId, mensajesDeLaVentana(actorId, ahora), ahora);
        if (patron.isEmpty()) {
            return Optional.empty();
        }
        avisarAQuienPuedaActuar(patron.get(), ahora);
        return mensajeDeApoyo.paraMostrar();
    }

    /**
     * El mensaje que la persona acaba de escribir YA esta guardado cuando se llega aca
     * ({@code ConversacionRenasiaService} lo persiste antes de preguntarle al modelo), asi que
     * entra en esta lectura y se cuenta. Es lo que hace que el tercero dispare en su propio turno.
     */
    private List<MensajeRenasia> mensajesDeLaVentana(UserId actorId, Instant ahora) {
        return loadMensajeRenasiaPort.escritosPorElUsuarioDesde(actorId,
                PatronDeMalestarRepetido.inicioDeLaVentana(ahora));
    }

    /**
     * Publica el evento. Quienes son ADMIN/ALQUIMISTA lo resuelve {@code notifications} al
     * escucharlo, igual que con {@code GrupoPorVencerEvent}: `rag` no tiene por que saber a quien le
     * llega un aviso.
     */
    private void avisarAQuienPuedaActuar(PatronDeMalestarRepetido patron, Instant ahora) {
        String nombre = userSummaryFinder.findById(patron.usuarioId())
                .map(UserSummary::fullName)
                .filter(valor -> !valor.isBlank())
                .orElse(SIN_NOMBRE);
        publisher.publishEvent(new PatronDeMalestarRepetidoEvent(patron.claveDeDeduplicacion(),
                patron.usuarioId().value(), nombre, patron.detecciones(),
                PatronDeMalestarRepetido.diasDeLaVentana(), ahora));
        // Sin el id del actor y sin una sola palabra de lo que escribio: las dos cosas son dato
        // personal (CLAUDE.MD §5.4.9). La deduplicacion hace que esta linea no se repita por
        // episodio aunque el metodo si se ejecute en cada mensaje posterior.
        log.info("[rag.PatronDeMalestarService] patron repetido: {} detecciones en {} dias; aviso publicado",
                patron.detecciones(), PatronDeMalestarRepetido.diasDeLaVentana());
    }
}
