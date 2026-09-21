package com.renaser.os.community.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato publico de `community` para que OTRO modulo pregunte si alguien publico en el
 * Muro dentro de una ventana de tiempo, sin leerle la tabla `publicaciones_muro` de frente
 * (D-41: ningun modulo consulta la tabla de otro).
 *
 * <p>Su primer consumidor es `habits`: el dueno del producto pidio (2026-09-04) que el
 * habito POST DIARIO EN COMUNIDAD solo se de por cumplido si el aprendiz publico de verdad
 * — y que esa comprobacion la haga el SERVIDOR, no la palabra del cliente. Sin este puerto,
 * `habits` tendria que creerle a un flag mandado desde el telefono.
 *
 * <p><b>Es una consulta, no una autorizacion.</b> A diferencia de {@link PublicarEnMuroPort},
 * no valida rol ni estado de cuenta del autor: responde por un hecho ya ocurrido. Quien
 * llama ya autorizo a su actor por su cuenta (en `habits`, {@code RegistroService.requireSelf}).
 */
public interface PublicacionMuroFinder {

    /**
     * Si {@code autorId} tiene al menos UNA publicacion propia creada dentro de
     * {@code [desde, hasta)} — {@code desde} inclusive, {@code hasta} exclusivo, la
     * convencion de media ventana que evita tanto el hueco como el solape entre dos dias
     * consecutivos.
     *
     * <p><b>La ventana la calcula el llamador, a proposito.</b> Este modulo no sabe en que
     * zona horaria vive un participante ni cuando empieza su dia — eso es de
     * `users`/`habits` (regla 02-tiempo-zonas-y-schedulers: "para el dia de alguien va
     * siempre {@code clock.now().atZone(participacion.timezone()).toLocalDate()}"). Aca
     * entran dos instantes ya resueltos y se comparan contra `creado_en`, que es
     * `timestamptz`.
     *
     * <p><b>Que cuenta como publicar</b> (decidido asi por lo que hoy se puede sostener con
     * el dato, NO por una regla que el dueno haya dictado — sigue como pregunta abierta en
     * el informe del cambio):
     * <ul>
     *   <li><b>Cuenta</b> una publicacion propia, de cualquier {@code TipoPublicacion}.</li>
     *   <li><b>Cuenta</b> aunque este {@code oculta}: ocultar es un acto de MODERACION
     *       posterior y con semantica propia (V1: "distinta de borrar"). Que un moderador
     *       pueda, sin querer, revocarle a alguien un habito ya cumplido seria un efecto
     *       secundario que nadie pidio. Ojo: el feed publico SI filtra `oculta = false`,
     *       asi que este metodo responde distinto que {@code feed()} a proposito.</li>
     *   <li><b>NO cuenta</b> un comentario en la publicacion de otro: vive en
     *       `comentarios_muro`, es otro agregado, y "publicar algo" en el pedido del dueno
     *       nombra el acto de publicar. Si el dueno confirma que comentar tambien vale, se
     *       agrega un metodo aca — no se cambia el significado de este en silencio.</li>
     * </ul>
     */
    boolean publicoEntre(UserId autorId, Instant desde, Instant hasta);

    /**
     * La publicacion lista para que otro modulo la comparta, o vacio si no existe.
     *
     * <p><b>Se agrega un metodo en vez de cambiar el de arriba</b>, siguiendo lo que este mismo
     * contrato ya deja escrito para el caso de los comentarios: <i>"se agrega un metodo aca — no se
     * cambia el significado de este en silencio"</i>.
     *
     * <p><b>Su consumidor es `chat`</b>, para compartir una publicacion del Muro en una
     * conversacion. Devuelve la REFERENCIA al objeto de S3 (bucket + ruta), no una URL firmada:
     * el motivo esta explicado en {@link PublicacionParaCompartir}, y es la correccion de un bug
     * por el que la foto compartida moria a los 15 minutos.
     *
     * <p><b>Es una consulta, no una autorizacion</b>, igual que {@link #publicoEntre}: responde por
     * una publicacion que existe. Quien llama ya autorizo a su actor — en `chat`, comprobando que
     * sea participante de la conversacion donde va a compartir.
     *
     * <p><b>NO devuelve las ocultas</b>, al reves que {@link #publicoEntre}. Este javadoc decia lo
     * contrario hasta el 2026-09-21 y ya no era cierto: el 2026-09-18 la implementacion sumo un
     * {@code .filter(publicacion -> !publicacion.oculta())} porque compartir al chat resucitaba la
     * foto de una publicacion que el autor habia borrado —"borrar mi publicacion" solo pone
     * {@code oculta}— o que un moderador habia retirado, y {@code MensajeService.urlDeLectura} la
     * volvia a prefirmar para todo participante de la conversacion destino.
     *
     * <p><b>Esa puerta es ahora la UNICA que cruza la media del Muro para entrar al chat</b>, asi
     * que este filtro es carga estructural, no una preferencia: desde el 2026-09-21
     * {@code MensajeService} acepta una ruta ajena al prefijo de la conversacion solo cuando viene
     * marcada como derivada por el servidor ({@code OrigenMedia.MURO_COMPARTIDO}), y el unico que
     * pone esa marca es {@code CompartirPublicacionService}, justo despues de llamar a este
     * metodo. Quitar el filtro de aca reabre el agujero entero sin tocar una linea de `chat`.
     *
     * <p>Que {@link #publicoEntre} siga contando las ocultas no es una contradiccion: aquel
     * responde por un hecho ya ocurrido (el aprendiz publico ese dia) y este entrega contenido
     * para volver a mostrarlo.
     */
    Optional<PublicacionParaCompartir> paraCompartir(UUID publicacionId);
}
