package com.renaser.os.rag.domain.model.aviso;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Que avisos de habito deja el acompanante en el chat, y con que texto (fase 5 de
 * {@code docs/arquitectura/PROPUESTA_ACOMPANANTE_90_DIAS.md} §5.1: "el acompanante escribe
 * primero").
 *
 * <p><b>Plantilla, no IA.</b> El texto se arma reemplazando marcadores con datos que ya calculo el
 * codigo — {@code habits} calcula los puntos (D-97) y el instante del aviso. Costo cero por aviso
 * y ninguna posibilidad de que el mensaje invente una hora o un puntaje.
 *
 * <p><b>Todo es configuracion, nada es constante</b>: si el aviso pasa al chat, cuales de los dos
 * tipos pasan y la redaccion quedaron <b>por decidir</b> en §5.1. Mismo criterio que
 * {@code MensajeDeApoyo}: una plantilla vacia apaga ese tipo, en vez de mostrar un texto que nadie
 * aprobo.
 *
 * <p>Marcadores reconocidos: {@code {habito}}, {@code {hora}} (HH:mm en la zona del aprendiz),
 * {@code {puntos}} y {@code {minutos}}. Uno que la plantilla no use simplemente no aparece; uno
 * desconocido queda literal (se ve el error de redaccion, no se esconde).
 *
 * @param activo     el interruptor general ({@code renaser.ia.acompanante.avisos-en-chat})
 * @param tipos      nombres de los tipos que pasan al chat — espejo como String de
 *                   {@code habits.TipoAvisoHabito}, igual que {@code AvisoHabitoDebidoEvent.tipoAviso}
 * @param plantillas texto por nombre de tipo
 */
public record AvisosEnChat(boolean activo, Set<String> tipos, Map<String, String> plantillas) {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public AvisosEnChat {
        tipos = tipos == null ? Set.of() : Set.copyOf(tipos);
        plantillas = plantillas == null ? Map.of() : Map.copyOf(plantillas);
    }

    public static AvisosEnChat apagados() {
        return new AvisosEnChat(false, Set.of(), Map.of());
    }

    /** Un tipo pasa al chat solo con el interruptor prendido, el tipo elegido y un texto escrito. */
    public boolean aplicaA(String tipoAviso) {
        return activo && tipoAviso != null && tipos.contains(tipoAviso) && !plantillaDe(tipoAviso).isBlank();
    }

    /** Vacio si ese tipo no pasa al chat (ver {@link #aplicaA}). */
    public Optional<String> redactar(String tipoAviso, DatosDelAviso datos) {
        if (!aplicaA(tipoAviso)) {
            return Optional.empty();
        }
        // {habito} al final a proposito: es el unico marcador con texto del usuario, y un titulo
        // que contuviera "{hora}" no debe terminar reemplazado.
        return Optional.of(plantillaDe(tipoAviso)
                .replace("{hora}", HORA.format(datos.hora()))
                .replace("{puntos}", String.valueOf(datos.puntos()))
                .replace("{minutos}", String.valueOf(datos.minutos()))
                .replace("{habito}", datos.habito())
                .strip());
    }

    private String plantillaDe(String tipoAviso) {
        String plantilla = plantillas.get(tipoAviso);
        return plantilla == null ? "" : plantilla;
    }

    /**
     * @param hora la hora LOCAL del aprendiz a la que empieza o vence el habito — ya convertida a su
     *             zona por quien llama (regla 02 §1)
     */
    public record DatosDelAviso(String habito, LocalTime hora, int puntos, long minutos) {
    }
}
