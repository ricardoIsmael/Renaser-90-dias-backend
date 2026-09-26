package com.renaser.os.chat.application.ports.in.conversacion;

import java.util.UUID;

/**
 * Deja abierto el chat de dos entre cada aprendiz de un grupo y quien lo acompaña: su mentor o,
 * en la recepción, cada guía (D-173).
 *
 * <p>Solo abre, nunca cierra: cuando el mentor rota, el chat con el anterior queda como historial,
 * igual que cualquier chat directo. Por eso no hace falta saber qué cambió en el grupo, y
 * correrlo dos veces o con un aviso viejo da lo mismo.
 */
public interface AbrirChatsConAcompananteUseCase {

    /** @return cuántos chats nuevos abrió */
    int abrirParaGrupo(UUID celulaId);
}
