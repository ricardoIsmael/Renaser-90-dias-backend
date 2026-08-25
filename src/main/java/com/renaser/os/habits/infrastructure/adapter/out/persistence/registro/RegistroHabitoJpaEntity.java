package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "registros_habito", schema = "renaser")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistroHabitoJpaEntity {

    @Id
    private UUID id;

    private UUID participanteId;

    private UUID habitoId;

    private LocalDate fechaEjecucion;

    private Short diaPrograma;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoDiaJpa tipoDia;

    private boolean esOpcional;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private EstadoRegistroJpa estado;

    private Short puntosOtorgados;

    private String respuestaTexto;

    private Short calificacionProductividad;

    private UUID entradaDiarioId;

    private Instant completadoEn;

    private Instant creadoEn;

    private Instant actualizadoEn;
}
