package com.renaser.os.habits.application.ports.in.aviso;

import com.renaser.os.shared.domain.UserId;

/**
 * Recalcula los avisos automaticos que le corresponden AHORA a un aprendiz y publica uno
 * {@code AvisoHabitoDebidoEvent} por cada uno.
 *
 * <p><b>Por participante y no por lote, a proposito</b> (regla 02 §4): el barrido que lo llama
 * itera el padron y le da a cada participante su propia transaccion corta, de modo que uno con
 * datos rotos — una zona horaria invalida, por ejemplo — no puede tumbar el aviso de los demas
 * ni revertir lo ya publicado.
 *
 * <p><b>Idempotente por construccion.</b> No lleva ningun estado propio: dos corridas seguidas
 * producen el mismo resultado porque los avisos se derivan del calendario y la deduplicacion
 * final la hace {@code notificaciones} por {@code origen_evento_id} (C-7/V16). Correrlo dos
 * veces no molesta a nadie; correrlo tarde simplemente pierde la franja, sin dejar el sistema
 * desalineado.
 *
 * @return cuantos avisos se publicaron (0 es lo normal la enorme mayoria de las corridas)
 */
public interface DespacharAvisosHabitoUseCase {

    int despacharDe(UserId participanteId);

    /**
     * De los {@code inscritos} dados, los unicos a los que vale la pena abrirles {@link #despacharDe}: los que tienen algun registro sin terminar en la
     * ventana de fechas que puede ser «hoy» para alguien (ayer, hoy y manana en UTC: la zona de cada persona la corre hasta un dia para cada lado).
     * Auditoria de consultas del 2026-11-02 (H-05): el barrido de cada cinco minutos visitaba a TODOS los inscritos, uno por transaccion y con varias
     * consultas cada uno (hasta 2,6 millones al dia); con esto visita a quien tiene algo vivo. {@code despacharDe} sigue decidiendo igual que antes
     * (con la fecha exacta en la zona de la persona): este filtro solo descarta a quien seguro no tiene nada.
     */
    java.util.List<UserId> conAlgoPorAvisar(java.util.Collection<UserId> inscritos);
}
