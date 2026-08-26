package com.renaser.os.rag.infrastructure.adapter.out.persistence.espejosombra;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Dueña real de {@code informes_espejo_sombra} + su tabla hija {@code preguntas_confrontacion}. */
@Entity
@Table(name = "informes_espejo_sombra", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InformeEspejoSombraJpaEntity {

    @Id
    private UUID id;

    @Column(name = "participante_id")
    private UUID participanteId;

    @Column(name = "semana_inicio")
    private LocalDate semanaInicio;

    @Column(name = "cantidad_entradas")
    private Short cantidadEntradas;

    @Column(name = "patron_dominante")
    private String patronDominante;

    @Column(name = "pct_pasado")
    private Short pctPasado;

    @Column(name = "pct_presente")
    private Short pctPresente;

    @Column(name = "pct_futuro")
    private Short pctFuturo;

    @Column(name = "insight")
    private String insight;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "preguntas_confrontacion", schema = "renaser", joinColumns = @JoinColumn(name = "informe_id"))
    @OrderBy("orden ASC")
    private List<PreguntaConfrontacionEmbeddable> preguntas = new ArrayList<>();

    @Column(name = "creado_en")
    private Instant creadoEn;
}
