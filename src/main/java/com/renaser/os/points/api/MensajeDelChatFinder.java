package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * El mensaje personalizado que acompaña a la tarjeta diaria del semáforo en el chat de soporte (D-281, spec 006). Lo
 * consume {@code chat}, que arma esa tarjeta a las 23:50 de cada aprendiz (D-223); la elección y la redacción son de
 * {@code points}, igual que el mensaje de Hoy (D-277): el mismo catálogo y los mismos datos de la persona.
 *
 * <p>Lo que distingue a este canal:
 * <ul>
 *   <li>Es del grupo del color del DÍA de la tarjeta, no del de la ventana de 7 días que muestra Hoy.</li>
 *   <li>Nunca dice cuánto falta para subir de color ({@code {faltan}}): a las 23:50 el día ya casi cerró.</li>
 *   <li>Es distinto del que la persona vio en Hoy ese día, y la regla de no repetir en 14 días cuenta los dos canales.</li>
 *   <li>Solo lleva texto: el chat no manda sticker.</li>
 * </ul>
 *
 * <p>Es idempotente por persona, fecha y color: pedirlo otra vez (el reintento de las 23:55) devuelve el mismo mensaje.
 * No autoriza: quien llama decide a quién le corresponde la tarjeta.
 */
public interface MensajeDelChatFinder {

    /**
     * @param aprendiz la persona de la tarjeta
     * @param fecha    el día LOCAL de la tarjeta (el de la zona de la persona, nunca el del servidor)
     * @param color    el color de ese día: {@code VERDE}, {@code AMARILLO} o {@code ROJO}; con {@code SIN_DATOS} no hay mensaje
     * @return el texto ya escrito (sin llaves, en tuteo, hasta 300 caracteres), o vacío si no hay ningún mensaje posible
     *         para esa persona y ese día
     */
    Optional<String> delDia(UserId aprendiz, LocalDate fecha, ColorSemaforo color);
}
