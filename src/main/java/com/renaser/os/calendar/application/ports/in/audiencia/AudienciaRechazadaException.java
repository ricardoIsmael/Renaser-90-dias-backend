package com.renaser.os.calendar.application.ports.in.audiencia;

import java.util.List;
import java.util.UUID;

/**
 * La audiencia elegida no se puede guardar (spec 044). Viaja con su {@code codigo} para que la app lo reconozca sin leer el texto:
 * <ul>
 *   <li>{@link #FUERA_DE_TU_ALCANCE} (403, FR-004): alguna persona o grupo no le corresponde a quien guarda; {@code ids} dice cuáles.</li>
 *   <li>{@link #AUDIENCIA_VACIA} (400, FR-005): hoy no hay nadie en lo elegido.</li>
 *   <li>{@link #AUDIENCIA_INVALIDA} (400, FR-005): faltan las partes, o una parte no tiene lo que exige.</li>
 * </ul>
 */
public class AudienciaRechazadaException extends RuntimeException {

    public static final String FUERA_DE_TU_ALCANCE = "FUERA_DE_TU_ALCANCE";
    public static final String AUDIENCIA_VACIA = "AUDIENCIA_VACIA";
    public static final String AUDIENCIA_INVALIDA = "AUDIENCIA_INVALIDA";

    private final String codigo;
    private final List<UUID> ids;

    public AudienciaRechazadaException(String codigo, String mensaje, List<UUID> ids) {
        super(mensaje);
        this.codigo = codigo;
        this.ids = ids == null ? List.of() : List.copyOf(ids);
    }

    public static AudienciaRechazadaException fueraDeTuAlcance(List<UUID> ids) {
        return new AudienciaRechazadaException(FUERA_DE_TU_ALCANCE, "Hay personas o grupos que no puedes invitar.", ids);
    }

    public static AudienciaRechazadaException vacia() {
        return new AudienciaRechazadaException(AUDIENCIA_VACIA, "Hoy no hay nadie en lo que elegiste. Elige otra opción o suma personas.", null);
    }

    public static AudienciaRechazadaException invalida(String mensaje) {
        return new AudienciaRechazadaException(AUDIENCIA_INVALIDA, mensaje, null);
    }

    public String codigo() {
        return codigo;
    }

    public List<UUID> ids() {
        return ids;
    }

    public boolean esDeAlcance() {
        return FUERA_DE_TU_ALCANCE.equals(codigo);
    }
}
