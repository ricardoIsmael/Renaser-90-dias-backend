package com.renaser.os.calendar.infrastructure.adapter.out.persistence.confirmacion;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmacionEventoId implements Serializable {

    private UUID eventoId;
    private Instant inicioOcurrencia;
    private UUID usuarioId;
}
