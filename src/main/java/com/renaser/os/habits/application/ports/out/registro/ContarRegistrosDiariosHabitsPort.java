package com.renaser.os.habits.application.ports.out.registro;

import com.renaser.os.habits.domain.model.registro.ConteoDiarioHabitos;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Consulta EN LOTE (D-43, docs/MODULOS_A_AVANZAR.md §8): agrega, en UNA sola
 * consulta a {@code registros_habito}, los conteos diarios crudos de TODOS los
 * participantes pedidos dentro de la ventana [desde, hasta]. Una implementacion
 * que por dentro haga una consulta por participante no cumple el contrato — es
 * exactamente el N+1 que motivo la decision (incidente real: "Too many database
 * connections opened" con ~30 cuentas activas).
 *
 * <p>Participantes sin ningun registro en la ventana simplemente no aparecen en
 * el mapa devuelto (o aparecen con lista vacia) — el llamador decide que hacer
 * con "sin datos" (ver {@code PorcentajeHabitos#SIN_DIAS_CALIFICABLES}).
 */
public interface ContarRegistrosDiariosHabitsPort {

    Map<UserId, List<ConteoDiarioHabitos>> contarPorParticipanteYDia(Collection<UserId> participantes,
                                                                       LocalDate desde, LocalDate hasta);
}
