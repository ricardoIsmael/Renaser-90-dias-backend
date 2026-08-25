package com.renaser.os.chat.application.ports.in.conversacion;

import java.util.UUID;

/**
 * Crea la conversacion CELULA de una celula recien creada, de forma idempotente (protegida
 * ademas por el indice unico {@code celulas.id} -> {@code conversaciones.celula_id UNIQUE}).
 * Sin comando propio: lo dispara solo {@code CelulaCreadaChatListener} (adapter/in/event) al
 * escuchar {@code community.api.CelulaCreadaEvent}.
 *
 * <p>Solo crea la fila de la conversacion — agregar a los miembros de la celula como
 * participantes queda fuera de alcance (ver docs/MODULO_CHAT.md §6: `community` no publica
 * hoy un evento de "miembro agregado/quitado de celula").
 */
public interface CrearConversacionCelulaUseCase {

    void crearParaCelula(UUID celulaId);
}
