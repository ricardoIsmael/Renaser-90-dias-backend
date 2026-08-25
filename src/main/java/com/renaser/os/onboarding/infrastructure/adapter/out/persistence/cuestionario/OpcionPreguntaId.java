package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.cuestionario;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Clase de PK compuesta para OpcionPreguntaJpaEntity (pregunta_id, orden). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpcionPreguntaId implements Serializable {

    private Integer preguntaId;
    private Short orden;
}
