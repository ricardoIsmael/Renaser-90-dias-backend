package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.mapa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/** `protocolos_reemplazo_mapa` (V41). Los cuatro textos son contenido del aprendiz. */
@Entity
@Table(name = "protocolos_reemplazo_mapa", schema = "renaser")
@Data
@ToString(exclude = {"patron", "disparador", "conductaActual", "respuestaAlternativa"})
@NoArgsConstructor
@AllArgsConstructor
public class ProtocoloReemplazoMapaJpaEntity {

    @Id
    private UUID id;

    private UUID usuarioId;

    private String protocoloId;

    private String patron;

    private String disparador;

    private String conductaActual;

    private String respuestaAlternativa;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
