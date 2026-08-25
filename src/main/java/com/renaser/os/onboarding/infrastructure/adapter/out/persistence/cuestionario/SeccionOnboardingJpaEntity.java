package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.cuestionario;

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
@Table(name = "secciones_onboarding", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeccionOnboardingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    private String flujo;

    private String claveSeccion;

    private String titulo;

    private String descripcion;

    private Short orden;

    private Instant creadoEn;
}
