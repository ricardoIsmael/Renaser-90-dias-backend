package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido;
import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido.RespuestaPreview;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.domain.model.mensaje.Cita;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lo que la app necesita para dibujar mensajes sin otra llamada (#29, D-251): nombre y foto de quien escribe,
 * la URL firmada del adjunto y el resumen del mensaje citado.
 *
 * <p>Salió de {@code MensajeService} (que ya pasaba las 400 líneas) cuando la respuesta de enviar empezó a
 * traer también el resumen de la cita (D-251), sin cambiar el criterio: todo EN LOTE por página — una
 * consulta para los citados y una para las personas —, nunca una por mensaje (mismo criterio que
 * {@code TracksDelDiaProyeccionService} de `habits`). No es un bean: lo arma {@code MensajeService} con sus
 * mismos puertos.
 */
final class MensajesParaMostrar {

    private static final Duration VALIDEZ_URL_LECTURA = Duration.ofMinutes(15);

    private final LoadMensajePort loadMensajePort;
    private final UserSummaryFinder userSummaryFinder;
    private final AlmacenamientoPort almacenamientoPort;

    MensajesParaMostrar(LoadMensajePort loadMensajePort, UserSummaryFinder userSummaryFinder,
                        AlmacenamientoPort almacenamientoPort) {
        this.loadMensajePort = loadMensajePort;
        this.userSummaryFinder = userSummaryFinder;
        this.almacenamientoPort = almacenamientoPort;
    }

    /**
     * La página del listado. Como mucho DOS consultas en lote — {@code loadMensajePort.porIds} (los citados) y
     * {@code userSummaryFinder.findByIds} (todos los emisores, de los mensajes y de los citados) —, ninguna si
     * la página está vacía.
     */
    List<MensajeEnriquecido> deLaPagina(List<Mensaje> mensajes, UserId quienMira) {
        if (mensajes.isEmpty()) {
            return List.of();
        }
        List<MensajeId> idsCitados = mensajes.stream().map(Mensaje::respuestaAId).filter(Objects::nonNull)
                .distinct().toList();
        Map<MensajeId, Mensaje> citados = idsCitados.isEmpty() ? Map.of() : loadMensajePort.porIds(idsCitados);

        // Los mensajes del programa no se atribuyen a la persona guardada en emisor_id (D-199): no se la busca.
        Set<UserId> idsUsuarios = new LinkedHashSet<>();
        mensajes.stream().filter(m -> !m.esDelPrograma()).forEach(m -> idsUsuarios.add(m.emisorId()));
        citados.values().stream().filter(c -> !c.esDelPrograma()).forEach(c -> idsUsuarios.add(c.emisorId()));
        Map<UserId, UserSummary> usuarios = userSummaryFinder.findByIds(idsUsuarios);

        return mensajes.stream()
                .map(m -> enriquecido(m, resumenSiSePuede(m, citadoDe(m, citados), usuarios, quienMira), usuarios))
                .toList();
    }

    /**
     * {@code null} si no responde a nada o si el citado no vino. Sin el {@code esRespuesta()} delante,
     * {@code Map.of().get(null)} tira {@code NullPointerException}: los mapas inmutables no aceptan una clave
     * nula ni para preguntar (E-509).
     */
    private static Mensaje citadoDe(Mensaje mensaje, Map<MensajeId, Mensaje> citados) {
        return mensaje.esRespuesta() ? citados.get(mensaje.respuestaAId()) : null;
    }

    /**
     * La respuesta de enviar: como hasta D-251, sin nombre, foto ni URL del mensaje propio (la app ya los
     * tiene y los toma de lo que mandó), pero con el resumen de lo que cita. El citado es el que validó
     * {@link Cita#aResponder} en esta misma transacción: no se vuelve a buscar.
     */
    MensajeEnriquecido recienEnviado(Mensaje mensaje, Cita cita, UserId quienEscribe) {
        if (cita == null) {
            return new MensajeEnriquecido(mensaje, null, null, null, null);
        }
        Mensaje citado = cita.citado();
        Map<UserId, UserSummary> autor = citado.esDelPrograma() ? Map.of()
                : userSummaryFinder.findById(citado.emisorId()).map(u -> Map.of(u.id(), u)).orElse(Map.of());
        return new MensajeEnriquecido(mensaje, null, null, resumen(citado, autor, quienEscribe), null);
    }

    private MensajeEnriquecido enriquecido(Mensaje mensaje, RespuestaPreview resumen, Map<UserId, UserSummary> usuarios) {
        if (mensaje.esDelPrograma()) {
            return new MensajeEnriquecido(mensaje, Mensaje.NOMBRE_DEL_PROGRAMA, null, resumen, urlDeLectura(mensaje));
        }
        UserSummary emisor = usuarios.get(mensaje.emisorId());
        return new MensajeEnriquecido(mensaje, emisor != null ? emisor.fullName() : null,
                emisor != null ? emisor.avatarUrl() : null, resumen, urlDeLectura(mensaje));
    }

    /**
     * {@code null} si no responde a nada o si el citado no se puede mostrar ({@link Cita#sePuedeMostrar}):
     * borrado, retirado, o de otra conversación. En los dos últimos casos la fila existe, pero su texto no sale.
     */
    private RespuestaPreview resumenSiSePuede(Mensaje mensaje, Mensaje citado, Map<UserId, UserSummary> usuarios,
                                              UserId quienMira) {
        return Cita.sePuedeMostrar(mensaje, citado) ? resumen(citado, usuarios, quienMira) : null;
    }

    private RespuestaPreview resumen(Mensaje citado, Map<UserId, UserSummary> usuarios, UserId quienMira) {
        String miniatura = esImagen(citado) ? urlDeLectura(citado) : null;
        return new RespuestaPreview(citado.id(), nombreDeQuienFirma(citado, usuarios), citado.tipo(),
                extracto(citado.texto()), citado.mediaMime(), citado.mediaDuracionS(), miniatura,
                citado.escritoPor(quienMira));
    }

    /** Una foto, un sticker o la tarjeta del programa: lo que la cita puede mostrar en miniatura. */
    private static boolean esImagen(Mensaje mensaje) {
        return mensaje.mediaRuta() != null && mensaje.mediaMime() != null && mensaje.mediaMime().startsWith("image/");
    }

    /**
     * Se firma por mensaje y no en lote porque {@code firmarLectura} es cálculo local del SDK (no hay ida y
     * vuelta a S3), así que no es una consulta N+1.
     */
    private String urlDeLectura(Mensaje mensaje) {
        if (mensaje.mediaRuta() == null) {
            return null;
        }
        return almacenamientoPort.firmarLectura(mensaje.mediaRuta(), VALIDEZ_URL_LECTURA).toString();
    }

    /** El programa firma sus mensajes; los demás, su emisor (o nadie si su cuenta ya no está). */
    private static String nombreDeQuienFirma(Mensaje mensaje, Map<UserId, UserSummary> usuarios) {
        if (mensaje.esDelPrograma()) {
            return Mensaje.NOMBRE_DEL_PROGRAMA;
        }
        UserSummary emisor = usuarios.get(mensaje.emisorId());
        return emisor != null ? emisor.fullName() : null;
    }

    /**
     * Los primeros {@link MensajeEnriquecido#LARGO_PREVIEW} caracteres, en una línea (los saltos y espacios
     * seguidos se juntan en uno: la cita es un renglón). Se corta por caracteres Unicode y no con
     * {@code substring} a secas: cortar en la unidad UTF-16 número 80 partía un emoji por la mitad y dejaba
     * un carácter roto en el JSON (D-251).
     */
    static String extracto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String renglon = texto.strip().replaceAll("\\s+", " ");
        if (renglon.codePointCount(0, renglon.length()) <= MensajeEnriquecido.LARGO_PREVIEW) {
            return renglon;
        }
        return renglon.substring(0, renglon.offsetByCodePoints(0, MensajeEnriquecido.LARGO_PREVIEW)).stripTrailing()
                + "…";
    }
}
