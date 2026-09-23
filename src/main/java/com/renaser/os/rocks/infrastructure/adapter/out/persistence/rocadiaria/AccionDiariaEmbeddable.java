package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocadiaria;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Fila de `acciones_diarias` (V61): el desglose de un objetivo del dia, hasta tres. */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccionDiariaEmbeddable {

    @Column(name = "orden")
    private Short orden;

    @Column(name = "descripcion")
    private String descripcion;
}
