package com.renaser.os.users.infrastructure.adapter.out.persistence.emergencia;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Tabla `solicitudes_emergencia` (V90, D-244). */
@Entity
@Table(name = "solicitudes_emergencia", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudDeEmergenciaJpaEntity {

    @Id
    private UUID id;

    private UUID aprendizId;

    private String queOcurrio;

    private short diaPedido;

    private short diaAlPedir;

    private String estado;

    private Instant creadaEn;

    private Instant resueltaEn;

    private UUID resueltaPor;

    private Short diaAplicado;
}
