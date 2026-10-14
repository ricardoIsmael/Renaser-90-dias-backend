package com.renaser.os.rag.application.ports.out.circuito;

import com.renaser.os.rag.domain.model.circuito.ParteDeLaIa;

/**
 * Avisa a las demás instancias que el circuito de esa parte cambió, para que relean su copia en memoria sin esperar
 * la recarga periódica. Fire-and-forget: si el aviso no llega, cada instancia se pone al día sola.
 */
public interface AvisarCambioDelCircuitoPort {

    void avisar(ParteDeLaIa parte);
}
