package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocasemanal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccionCriticaEmbeddable {

    @Column(name = "orden")
    private Short orden;

    @Column(name = "descripcion")
    private String descripcion;
}
