package com.renaser.os.rag.domain.model.conversacion;

/**
 * Por donde le llega la respuesta a la persona (2026-09-23): leida en pantalla o escuchada.
 *
 * <ul>
 *   <li>{@link #TEXTO}: el chat escrito de siempre. Es el valor por defecto y el unico que conocen
 *   las apps anteriores a este cambio.</li>
 *   <li>{@link #VOZ}: la persona le hablo al orbe de la pantalla Hoy y la respuesta se va a
 *   escuchar. Cambia la FORMA de la respuesta (frases cortas, sin markdown, sin listas, horas y
 *   numeros dichos como se hablan) — nunca el contenido, las fuentes, las herramientas ni los
 *   limites de seguridad.</li>
 * </ul>
 *
 * <p>No se persiste: el mensaje guardado es el mismo, venga de donde venga. Es una indicacion de
 * presentacion para el turno, no un dato de la conversacion.
 */
public enum CanalConversacion {
    TEXTO,
    VOZ
}
