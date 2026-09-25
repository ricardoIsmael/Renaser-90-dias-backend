package com.renaser.os.rag.application.ports.in.memoria;

import com.renaser.os.shared.domain.UserId;

/**
 * Despues de cada turno con el acompanante: si ya hay bastantes mensajes fuera de la ventana de los
 * ultimos 10, se comprimen en memoria (D-167). No bloquea: corre en segundo plano, y si falla se
 * reintenta en el turno siguiente.
 */
public interface CompactarMemoriaUseCase {

    void compactarEnSegundoPlano(UserId actorId);
}
