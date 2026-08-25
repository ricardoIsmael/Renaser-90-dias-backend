package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolDestinoEventoId implements Serializable {

    private UUID eventoId;
    private Short rolId;
}
