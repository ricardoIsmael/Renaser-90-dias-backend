package com.renaser.os.community.infrastructure.adapter.out.persistence.categoria;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "categorias_muro", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaMuroJpaEntity {

    @Id
    private String clave;

    private String etiqueta;

    private String emoji;

    private int orden;

    private boolean activa;

    private boolean esSistema;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
