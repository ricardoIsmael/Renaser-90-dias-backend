package com.renaser.os.habits.infrastructure.adapter.in.rest.santuario;

import com.renaser.os.habits.domain.model.santuario.MotivoSalidaBloqueo;
import jakarta.validation.constraints.NotNull;

public record RomperSantuarioRequest(@NotNull MotivoSalidaBloqueo motivo, String evidenciaBucket,
                                      String evidenciaRuta) {
}
