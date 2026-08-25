package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "excepciones_evento", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExcepcionEventoJpaEntity {

    @Id
    private UUID id;

    private UUID eventoId;

    private Instant inicioOcurrencia;

    private boolean cancelada;

    private Instant nuevoInicio;

    private Integer nuevaDuracion;

    private String nuevoTitulo;

    private Instant creadoEn;
}
