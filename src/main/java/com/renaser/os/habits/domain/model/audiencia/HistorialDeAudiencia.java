package com.renaser.os.habits.domain.model.audiencia;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Las versiones de la audiencia de UN hábito, de la más vieja a la más nueva. Append-only: nada se reescribe, así un
 * día pasado siempre se resuelve igual.
 */
public final class HistorialDeAudiencia {

    private static final HistorialDeAudiencia VACIO = new HistorialDeAudiencia(List.of());

    private final List<VersionDeAudiencia> versiones;

    private HistorialDeAudiencia(List<VersionDeAudiencia> versiones) {
        this.versiones = versiones;
    }

    public static HistorialDeAudiencia vacio() {
        return VACIO;
    }

    public static HistorialDeAudiencia de(List<VersionDeAudiencia> versiones) {
        return new HistorialDeAudiencia(versiones.stream()
                .sorted(Comparator.comparing(VersionDeAudiencia::rigeDesde)
                        .thenComparing(VersionDeAudiencia::cambiadoEn))
                .toList());
    }

    /**
     * La audiencia del día local {@code dia} en {@code zona}: la última guardada antes de ese día. Si ninguna lo
     * estaba, rige la primera — la audiencia con la que se creó el hábito cuenta desde la creación (clarify
     * 2026-10-12); solo los cambios esperan al día siguiente. Sin versiones, para todos.
     */
    public Audiencia vigenteEl(LocalDate dia, ZoneId zona) {
        if (versiones.isEmpty()) {
            return Audiencia.PARA_TODOS;
        }
        Audiencia vigente = versiones.getFirst().audiencia();
        for (VersionDeAudiencia version : versiones) {
            if (VigenciaPorDia.rigeEl(version.rigeDesde(), dia, zona)) {
                vigente = version.audiencia();
            }
        }
        return vigente;
    }

    /**
     * Si la última versión es un CAMBIO guardado el día local de hoy en {@code zona}: entonces todavía no rige (rige
     * desde mañana). La versión con la que nace un hábito no cuenta como cambio: rige desde la creación (E2E del
     * 2026-10-12: la lista decía «Rige desde mañana» en un hábito recién creado).
     */
    public boolean cambioPendienteHasta(Instant ahora, ZoneId zona) {
        if (versiones.size() < 2) {
            return false;
        }
        return versiones.getLast().rigeDesde().atZone(zona).toLocalDate().equals(ahora.atZone(zona).toLocalDate());
    }

    /** La última guardada, rija o no todavía: es la que ve y edita el administrador. */
    public Optional<VersionDeAudiencia> ultima() {
        return versiones.isEmpty() ? Optional.empty() : Optional.of(versiones.getLast());
    }
}
