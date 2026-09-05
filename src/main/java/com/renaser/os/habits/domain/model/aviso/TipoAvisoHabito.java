package com.renaser.os.habits.domain.model.aviso;

import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Los DOS avisos automaticos que recibe un aprendiz por cada habito de su dia (pedido del
 * dueno, 2026-09-05): que esta por empezar, y que esta por vencer.
 *
 * <p><b>Sobre {@link #claveIdempotencia}:</b> este modulo NO guarda una cola de avisos
 * enviados — a diferencia de {@code calendar}, que si tiene {@code recordatorios_evento}. El
 * aviso de un habito es enteramente DERIVABLE del calendario (regla 02 §2: derivar, no
 * incrementar): dado el registro, su ventana y la hora actual, siempre se puede recalcular si
 * toca avisar, sin ningun estado propio. Lo unico que hace falta es no mandar el mismo aviso
 * dos veces, y para eso ya existe la deduplicacion de {@code notificaciones} por
 * {@code (usuario_id, tipo, origen_evento_id)} (C-7, V16). Esta clave es ese
 * {@code origen_evento_id}: un UUID DETERMINISTICO derivado del registro y del tipo de aviso,
 * asi que el segundo intento choca contra el indice unico y se descarta solo.
 *
 * <p>No se usa {@code registroId} pelado como clave porque los dos avisos comparten el mismo
 * {@code TipoNotificacion.RECORDATORIO_HABITO}: sin el prefijo, el aviso de vencimiento seria
 * descartado como duplicado del de inicio.
 *
 * <p>{@code UUID.nameUUIDFromBytes} y no {@code randomUUID}: es una funcion pura del nombre,
 * lo que mantiene el dominio determinista (regla que {@code ArchitectureTest} verifica).
 */
public enum TipoAvisoHabito {

    /** "Tu habito empieza en X" — se dispara antes de la hora de disparo. */
    INICIO("aviso-habito-inicio:"),

    /** "Te queda por vencer" — se dispara antes del plazo de entrega, cuando el puntaje cae a 0. */
    POR_VENCER("aviso-habito-por-vencer:");

    private final String prefijoClave;

    TipoAvisoHabito(String prefijoClave) {
        this.prefijoClave = prefijoClave;
    }

    public UUID claveIdempotencia(RegistroHabitoId registroId) {
        return UUID.nameUUIDFromBytes((prefijoClave + registroId.value()).getBytes(StandardCharsets.UTF_8));
    }
}
