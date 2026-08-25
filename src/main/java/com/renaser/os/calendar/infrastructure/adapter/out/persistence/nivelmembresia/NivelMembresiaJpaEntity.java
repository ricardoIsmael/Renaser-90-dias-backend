package com.renaser.os.calendar.infrastructure.adapter.out.persistence.nivelmembresia;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "niveles_membresia", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NivelMembresiaJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    private Short rango;

    private String nombre;

    private Short pctProgresoMinimo;

    private Instant creadoEn;
}
