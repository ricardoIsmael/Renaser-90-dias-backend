package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.mapa;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * `acciones_mapa` (V41). Los dias son una {@code @ElementCollection} sobre `dias_accion_mapa`: una
 * fila por dia, como manda el esquema — el baseline ya habia rechazado los `text[]`.
 *
 * <p>{@code texto} es contenido del aprendiz: fuera del {@code toString()} (CLAUDE.md §5.4.9).
 */
@Entity
@Table(name = "acciones_mapa", schema = "renaser")
@Data
@ToString(exclude = "texto")
@NoArgsConstructor
@AllArgsConstructor
public class AccionMapaJpaEntity {

    @Id
    private UUID id;

    private UUID usuarioId;

    private String accionId;

    private String area;

    private String texto;

    private short frecuenciaSemanal;

    private String momento;

    private String evidencia;

    private UUID habitoId;

    private Instant creadoEn;

    private Instant actualizadoEn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dias_accion_mapa", schema = "renaser",
            joinColumns = @JoinColumn(name = "accion_mapa_id"))
    @Column(name = "dia_semana")
    private Set<Short> dias = new LinkedHashSet<>();
}
