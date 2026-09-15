package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.CompartirPublicacionUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.community.api.PublicacionMuroFinder;
import com.renaser.os.community.api.PublicacionParaCompartir;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * Compartir una publicacion del Muro en una conversacion (ver
 * {@link CompartirPublicacionUseCase} para el bug que esto corrige).
 *
 * <p><b>Por que un servicio propio y no un metodo mas en {@code MensajeService}.</b> Dos razones,
 * las dos verificables:
 * <ol>
 *   <li>{@code MensajeService} ya tiene 294 lineas y el techo duro de
 *       {@code .claude/rules/01-arquitectura-hexagonal} es <b>300</b>. Este caso de uso lo
 *       cruzaba.</li>
 *   <li>Es el criterio que CLAUDE.MD sec. 5.4.8 pide de entrada — <i>"una clase por caso de
 *       uso, no un {@code UserService} con 30 metodos"</i>. Que {@code MensajeService} agrupe
 *       tres casos de uso fue una conveniencia, no una regla a imitar.</li>
 * </ol>
 *
 * <p><b>La autorizacion NO se reescribe aca: la hace {@code enviar}.</b> Compartir es enviar un
 * mensaje, asi que se delega en {@link EnviarMensajeUseCase} y con eso corre el guard original
 * completo — actor activo, conversacion existente, y participacion con la revalidacion de grupo
 * contra la pertenencia vigente (una conversacion de CELULA no se autoriza contra
 * {@code participantes_conversacion}, que es una proyeccion y concede acceso de mas cuando se
 * queda vieja). Copiar ese guard en esta clase seria crear un segundo lugar del que se puede
 * desincronizar; delegar hace que sea imposible.
 *
 * <p><b>Consecuencia asumida del orden:</b> la publicacion se lee ANTES de que corra ese guard,
 * asi que un actor que no participa de la conversacion recibe 404 (no 403) si encima el id de
 * publicacion no existe. No hay fuga real: la existencia de una publicacion del Muro ya es
 * visible para cualquier usuario autenticado en {@code GET /api/v1/wall}, y el no-participante
 * sigue sin obtener ni el contenido ni el efecto. La alternativa era duplicar el guard, que es
 * peor.
 *
 * <p><b>Sin {@code @Transactional} propio</b>, a proposito: las dos lecturas van en la
 * transaccion implicita de su repositorio, y la unica escritura ocurre dentro del
 * {@code @Transactional} de {@code enviar}. Envolver todo en una transaccion mas larga solo
 * retendria una conexion de Hikari mientras se resuelven consultas de lectura.
 */
@Service
public class CompartirPublicacionService implements CompartirPublicacionUseCase {

    /**
     * Lo que ve el lector cuando el nombre del autor no se puede resolver. No es un valor
     * inventado: es literalmente el que ya usaba la app (<i>"const autor = post.author ?
     * post.author : 'Comunidad Renaser'"</i>), asi que el texto que se persiste sigue siendo el
     * que el usuario venia viendo. Preferible a reventar el envio, y muy preferible a escribir
     * "por null" en una fila que no caduca.
     */
    private static final String AUTOR_SIN_RESOLVER = "Comunidad Renaser";

    private final EnviarMensajeUseCase enviarMensajeUseCase;
    private final PublicacionMuroFinder publicacionMuroFinder;
    private final UserSummaryFinder userSummaryFinder;

    public CompartirPublicacionService(EnviarMensajeUseCase enviarMensajeUseCase,
                                        PublicacionMuroFinder publicacionMuroFinder,
                                        UserSummaryFinder userSummaryFinder) {
        this.enviarMensajeUseCase = enviarMensajeUseCase;
        this.publicacionMuroFinder = publicacionMuroFinder;
        this.userSummaryFinder = userSummaryFinder;
    }

    @Override
    public Mensaje compartir(CompartirPublicacionCommand command) {
        PublicacionParaCompartir publicacion = publicacionMuroFinder.paraCompartir(command.publicacionId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Publicacion no encontrada: " + command.publicacionId()));
        return enviarMensajeUseCase.enviar(new EnviarMensajeCommand(command.actorId(), command.conversacionId(),
                publicacion.tieneImagen() ? TipoMensaje.IMAGEN : TipoMensaje.TEXTO,
                textoDelMensaje(publicacion),
                publicacion.mediaBucket(), publicacion.mediaRuta(), publicacion.mediaMime(),
                // mediaBytes y mediaDuracionS quedan en null: son metadatos que el cliente
                // mide al subir su propio archivo, y aca no se sube nada — se referencia un
                // objeto que ya vive en el bucket del Muro. Una foto no tiene duracion.
                null, null,
                // Compartir no es responder a nadie.
                null));
    }

    /**
     * El MISMO texto que el usuario ya veia en el chat, menos la URL firmada que lo rompia
     * (comparar contra {@code handleShareToConversation}: encabezado, salto de linea + texto
     * entre comillas, y antes tambien una tercera linea con el enlace de S3 que ahora viaja
     * como media).
     *
     * <p>El pin de 📌 se deja como literal y no como {@code 📌} porque el archivo se
     * compila en UTF-8 ({@code spring-boot-starter-parent} fija
     * {@code project.build.sourceEncoding}); la prueba verifica el codepoint para que un cambio
     * de encoding no lo degrade en silencio.
     */
    private String textoDelMensaje(PublicacionParaCompartir publicacion) {
        String encabezado = "📌 [Compartido del Muro por " + nombreDelAutor(publicacion.autorId()) + "]";
        String texto = publicacion.texto();
        // Una foto sin epigrafe no lleva un par de comillas vacias colgando.
        if (texto == null || texto.isBlank()) {
            return encabezado;
        }
        return encabezado + "\n\"" + texto.strip() + "\"";
    }

    /**
     * El nombre lo resuelve el SERVIDOR contra {@code users.api}, nunca lo manda el cliente: el
     * texto queda persistido y atribuye una publicacion a una persona, asi que creerle al
     * telefono seria dejar que cualquiera firme un mensaje con el nombre de otro.
     */
    private String nombreDelAutor(UserId autorId) {
        return userSummaryFinder.findById(autorId)
                .map(UserSummary::fullName)
                .filter(nombre -> !nombre.isBlank())
                .orElse(AUTOR_SIN_RESOLVER);
    }
}
