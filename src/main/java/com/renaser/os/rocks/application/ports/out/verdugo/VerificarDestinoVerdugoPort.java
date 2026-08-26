package com.renaser.os.rocks.application.ports.out.verdugo;

import com.renaser.os.shared.domain.UserId;

import java.util.UUID;

/**
 * Verifica que el destino al que apunta un evento Verdugo le pertenezca al actor que lo
 * registra. Sin esto, cualquier aprendiz podia crear eventos Verdugo contra rocas o
 * registros de habito de OTRO participante (E-38, docs/BITACORA_ERRORES.md) — rompiendo
 * el invariante implicito de `eventos_verdugo` de que `participante_id` es dueño del
 * `roca_diaria_id`/`registro_habito_id` referenciado.
 *
 * <p>{@code registros_habito} es tabla de `habits`, no de `rocks`: se lee por consulta
 * propia y acotada a "¿pertenece a este participante?" — mismo criterio con el que este
 * modulo ya lee `participantes_programa` en {@code ConsultarProgresoParticipanteRocksPort},
 * sin importar tipos internos del otro modulo.
 */
public interface VerificarDestinoVerdugoPort {

    /** true si ese registro de habito existe Y pertenece al participante dado. */
    boolean registroHabitoPerteneceA(UUID registroHabitoId, UserId participanteId);
}
