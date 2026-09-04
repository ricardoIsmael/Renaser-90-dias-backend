package com.renaser.os.users.infrastructure.adapter.out.persistence.ajustediaprograma;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Tabla `ajustes_dia_programa` (V21). Append-only: sin metodos de mutacion propios. */
@Entity
@Table(name = "ajustes_dia_programa", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AjusteDiaProgramaJpaEntity {

    @Id
    private UUID id;

    private UUID participanteId;

    private short diaAnterior;

    private short diaNuevo;

    private short diasAjusteAnterior;

    private short diasAjusteNuevo;

    private String motivo;

    private UUID ajustadoPor;

    private Instant ajustadoEn;
}
