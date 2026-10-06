package com.renaser.os.phasecontracts.infrastructure.adapter.out.persistence.animal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "animales_de_fase", schema = "renaser")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
class AnimalDeFaseJpaEntity {

    @Id
    private short fase;

    private String nombreAnimal;

    private String imagenRuta;

    private UUID actualizadoPor;

    private Instant actualizadoEn;
}
