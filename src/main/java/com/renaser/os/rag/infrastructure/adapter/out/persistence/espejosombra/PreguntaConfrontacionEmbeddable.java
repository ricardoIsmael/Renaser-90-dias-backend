package com.renaser.os.rag.infrastructure.adapter.out.persistence.espejosombra;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Espejo de la tabla hija {@code preguntas_confrontacion} — no tiene @Entity propio: es parte del agregado. */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreguntaConfrontacionEmbeddable {

    @Column(name = "orden")
    private Short orden;

    @Column(name = "pregunta")
    private String pregunta;
}
