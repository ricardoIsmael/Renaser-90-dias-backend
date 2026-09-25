package com.renaser.os.rag.domain.model.logro;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Que logros celebra el acompanante en el chat, y con que texto. Mismo molde que
 * {@code AvisosEnChat} (D-155): plantilla, no IA — costo cero y ningun dato inventado.
 *
 * <p><b>Todo es configuracion, nada es constante.</b> Si se celebra, que se celebra y la redaccion
 * son decisiones del dueno todavia no tomadas. Una plantilla vacia apaga ese logro, en vez de
 * mostrar un texto que nadie aprobo.
 *
 * <p>Sin marcadores: los eventos de logro solo traen ids, y cualquier dato extra (titulo de la
 * roca, puntos) obligaria a una consulta mas por celebracion. Si el texto necesita uno, se agrega
 * aca con su test.
 *
 * @param activo     el interruptor general ({@code renaser.ia.acompanante.logros-en-chat})
 * @param tipos      los logros que pasan al chat
 * @param plantillas texto por logro
 */
public record LogrosEnChat(boolean activo, Set<TipoLogro> tipos, Map<TipoLogro, String> plantillas) {

    public LogrosEnChat {
        tipos = tipos == null ? Set.of() : Set.copyOf(tipos);
        plantillas = plantillas == null ? Map.of() : Map.copyOf(plantillas);
    }

    public static LogrosEnChat apagados() {
        return new LogrosEnChat(false, Set.of(), Map.of());
    }

    /** Un logro pasa al chat solo con el interruptor prendido, el tipo elegido y un texto escrito. */
    public boolean aplicaA(TipoLogro tipo) {
        return activo && tipo != null && tipos.contains(tipo) && !plantillaDe(tipo).isBlank();
    }

    /** Vacio si ese logro no pasa al chat (ver {@link #aplicaA}). */
    public Optional<String> redactar(TipoLogro tipo) {
        return aplicaA(tipo) ? Optional.of(plantillaDe(tipo).strip()) : Optional.empty();
    }

    private String plantillaDe(TipoLogro tipo) {
        String plantilla = plantillas.get(tipo);
        return plantilla == null ? "" : plantilla;
    }
}
