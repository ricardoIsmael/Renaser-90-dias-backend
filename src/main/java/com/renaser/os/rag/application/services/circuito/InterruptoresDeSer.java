package com.renaser.os.rag.application.services.circuito;

import com.renaser.os.rag.application.ports.in.circuito.CircuitoDeLaIa;
import com.renaser.os.rag.domain.model.circuito.ParteDeLaIa;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.application.ProcesosConInterruptor;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * Los interruptores de SER vistos desde adentro de {@code rag} (spec 002, FR-015 a FR-017): lo que decidió Soporte TI a
 * mano, combinado con el circuito automático. Cada método dice cómo responde una parte de SER cuando está apagada, para
 * que la app reciba lo que ya entiende:
 * <ul>
 *   <li>chat (SSE): {@code FuncionalidadApagadaException} <b>antes</b> de abrir el stream → 503 con el aviso;</li>
 *   <li>voz del orbe y voz en vivo: {@code false} → 204 (voz del teléfono) y cierre 1013 {@code no-disponible};</li>
 *   <li>propuestas: {@code false} → SER no ofrece ni crea propuestas;</li>
 *   <li>{@code ia} general: la memoria no se compacta.</li>
 * </ul>
 * Lo manual se pregunta primero y manda siempre (FR-017); el circuito, después, porque puede tomar el turno de prueba y
 * ese turno no se le puede dar a un pedido que lo manual igual iba a cortar.
 */
@Component
public class InterruptoresDeSer {

    public static final String IA = "ia";
    public static final String IA_CHAT = "ia.chat";
    public static final String IA_VOZ = "ia.voz";
    public static final String IA_PROPUESTAS = "ia.propuestas";

    private final Interruptores interruptores;
    private final CircuitoDeLaIa circuito;

    public InterruptoresDeSer(Interruptores interruptores, CircuitoDeLaIa circuito) {
        this.interruptores = interruptores;
        this.circuito = circuito;
    }

    /** El chat de SER y del curso: lanza con el aviso (manual o automático). Medio abierto, este pedido es la prueba. */
    public void exigirChat(UserId actor) {
        interruptores.exigirPrendidaPara(IA_CHAT, actor);
        circuito.exigirPaso(ParteDeLaIa.CHAT);
    }

    /** La voz del orbe y la voz en vivo. Medio abierto, {@code true} solo para el pedido que toma la prueba. */
    public boolean vozPara(UserId actor) {
        return interruptores.prendidaPara(IA_VOZ, actor) && circuito.dejaPasar(ParteDeLaIa.VOZ);
    }

    public boolean propuestasPara(UserId actor) {
        return interruptores.prendidaPara(IA_PROPUESTAS, actor);
    }

    /**
     * Compactar la memoria: no con {@code ia} apagada (política SALTA del catálogo), ni con el circuito del chat
     * abierto (sería llamar a un proveedor que no responde). Saltarla no pierde nada: se reintenta en otro turno.
     */
    public boolean puedeCompactarMemoria() {
        return !ProcesosConInterruptor.saltaEsteTurno(interruptores, IA, "rag.compactar-memoria")
                && !circuito.cortado(ParteDeLaIa.CHAT);
    }
}
