package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * Lectura del semáforo de cumplimiento del aprendiz para otros módulos (D-168): las vistas del
 * mentor, del líder y del administrador ({@code mentoring}) y los mensajes del acompañante
 * ({@code rag}).
 *
 * <p><b>Solo lee lo que el barrido horario ya guardó.</b> Ninguna lectura recalcula: por eso se
 * puede llamar en cada request sin costo (una consulta por tabla para toda la colección pedida).
 *
 * <p><b>No autoriza.</b> Igual que {@link HabitosDelDiaFinder}: quien llama ya estableció que el
 * actor puede ver a esas personas (relación vigente de mentor, rol administrativo, o él mismo).
 */
public interface SemaforoFinder {

    /**
     * La ventana vigente (los últimos 7 días cerrados, cada persona en su zona) de cada
     * participante pedido. Una colección vacía no consulta la base.
     *
     * @return sin clave = esa persona no se mide (sin programa activado)
     */
    Map<UserId, VentanaDelSemaforo> vigenteDe(Collection<UserId> participantes);

    /**
     * La semana sábado→viernes que termina en {@code semanaHasta} (un viernes; si no, lanza
     * {@link IllegalArgumentException}). Si la semana ya se cerró, trae la foto del cierre con
     * {@code cerrada = true}; si todavía corre, trae los días cerrados hasta ahora.
     *
     * @return sin clave = esa persona no se mide
     */
    Map<UserId, VentanaDelSemaforo> semanaDe(Collection<UserId> participantes, LocalDate semanaHasta);

    /**
     * El detalle completo de una persona.
     *
     * @param semanas cuántas semanas cerradas traer, entre 1 y 13 (se acota a ese rango)
     */
    DetalleDelSemaforo detalleDe(UserId participante, int semanas);
}
