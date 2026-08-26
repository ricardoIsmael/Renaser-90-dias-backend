package com.renaser.os.habits.infrastructure.adapter.in.rest.diario;

import com.renaser.os.habits.application.ports.in.diario.ConsultarBitacoraNocturnaUseCase.EstadoBitacoraHoy;
import com.renaser.os.habits.domain.model.diario.EntradaDiario;

import java.time.LocalDate;

public record JournalEntryResponse(LocalDate date, String type, boolean exists, String textContent,
                                    boolean hasAudio) {

    public static JournalEntryResponse from(EstadoBitacoraHoy estado) {
        return estado.existe() ? from(estado.entrada())
                : new JournalEntryResponse(estado.fecha(), "BITACORA_NOCTURNA", false, null, false);
    }

    public static JournalEntryResponse from(EntradaDiario entrada) {
        return new JournalEntryResponse(entrada.fecha(), entrada.tipo().name(), true, entrada.contenidoTexto(),
                entrada.audioRuta() != null);
    }
}
