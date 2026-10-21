package com.renaser.os.habits.domain.model.aviso;

import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
 * {@code origen_evento_id}: un UUID DETERMINISTICO derivado del registro, del tipo de aviso y del
 * momento avisado, asi que el segundo intento choca contra el indice unico y se descarta solo.
 *
 * <p>No se usa {@code registroId} pelado como clave porque los dos avisos comparten el mismo
 * {@code TipoNotificacion.RECORDATORIO_HABITO}: sin el prefijo, el aviso de vencimiento seria
 * descartado como duplicado del de inicio.
 *
 * <p>{@code UUID.nameUUIDFromBytes} y no {@code randomUUID}: es una funcion pura del nombre,
 * lo que mantiene el dominio determinista (regla que {@code ArchitectureTest} verifica).
 *
 * <p><b>La clave lleva el MOMENTO avisado (spec 007, FR-013).</b> Antes era solo registro y tipo: el aviso
 * de inicio salia una vez por dia y, si la persona movia la hora del habito DESPUES de enviado, el
 * servidor jamas volvia a avisar (la clave ya existia). Ahora el mismo registro y el mismo momento dan la
 * misma clave —los barridos de cada 5 minutos y las otras instancias no duplican— y una hora nueva da una
 * clave nueva, o sea un aviso mas. El {@code momento} sale de la ventana calculada con la hora del habito
 * ({@link AvisoHabito#momento()}), nunca del reloj del barrido: si saliera del reloj, cada vuelta daria una
 * clave distinta y el aviso se repetiria cada 5 minutos.
 *
 * <p>Efecto del dia del despliegue: todas las claves cambian de una vez, asi que un aviso ya enviado y
 * todavia dentro de su franja (15 min el de inicio, 30 el de vencimiento) saldria una vez mas. Por eso se
 * despliega en la madrugada de Lima, cuando casi no hay habitos con hora (research R6 de la spec 007).
 * Tampoco hay migracion: el indice unico {@code notificaciones_origen_evento_uk} (V16) no cambia.
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

    /**
     * Si este aviso tiene sentido para un registro en ese estado (FR-015b). El de inicio anuncia que algo
     * VA a empezar: solo mientras puede iniciarse ({@code PENDIENTE}). Una sesion especial ya abierta
     * ({@code EN_CURSO}) no lo recibe. El de vencimiento sirve en cualquier estado no terminal, y los
     * terminales ni llegan hasta aca.
     */
    public boolean correspondeA(EstadoRegistro estado) {
        return this != INICIO || estado.puedeIniciar();
    }

    /**
     * @param momento el instante avisado (hora de disparo o plazo de entrega). Se usa a la precision de
     *                SEGUNDOS: las horas de los habitos son {@code HH:mm}, y asi dos barridos nunca
     *                discrepan por una fraccion de segundo
     */
    public UUID claveIdempotencia(RegistroHabitoId registroId, Instant momento) {
        return UUID.nameUUIDFromBytes(
                (prefijoClave + registroId.value() + ":" + momento.getEpochSecond()).getBytes(StandardCharsets.UTF_8));
    }
}
