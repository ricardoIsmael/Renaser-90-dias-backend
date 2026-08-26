package com.renaser.os.habits.infrastructure.adapter.in.rest.diario;

import com.renaser.os.habits.domain.model.diario.EntradaDiario;

import java.time.LocalDate;

public record JournalEntryResponse(LocalDate date, String type, boolean exists, String textContent,
                                    boolean hasAudio) {

    public static JournalEntryResponse from(java.util.Optional<EntradaDiario> entrada, LocalDate hoy) {
        return entrada.map(e -> new JournalEntryResponse(e.fecha(), e.tipo().name(), true, e.contenidoTexto(),
                        e.audioRuta() != null))
                .orElseGet(() -> new JournalEntryResponse(hoy, "BITACORA_NOCTURNA", false, null, false));
    }
}
