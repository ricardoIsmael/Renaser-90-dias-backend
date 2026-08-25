package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.cuestionario;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "opciones_pregunta", schema = "renaser")
@IdClass(OpcionPreguntaId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpcionPreguntaJpaEntity {

    @Id
    private Integer preguntaId;

    @Id
    private Short orden;

    private String valor;

    private String etiqueta;
}
