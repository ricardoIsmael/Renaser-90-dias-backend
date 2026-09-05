package com.renaser.os.community.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

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
}
