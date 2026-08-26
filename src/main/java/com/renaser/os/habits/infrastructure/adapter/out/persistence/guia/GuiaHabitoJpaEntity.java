package com.renaser.os.habits.infrastructure.adapter.out.persistence.guia;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guias_habito", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuiaHabitoJpaEntity {

    @Id
    private UUID id;

    private UUID habitoId;

    private Short diaInicio;

    private Short diaFin;

    private String queHacer;

    private String comoHacerlo;

    private String ciencia;

    private String renaser;

    private String alquimia;

    private String resultados;

    private String mantraTitulo;

    private String mantraIntro;

    private String mantraCuerpo;

    private String referenciaFuente;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
