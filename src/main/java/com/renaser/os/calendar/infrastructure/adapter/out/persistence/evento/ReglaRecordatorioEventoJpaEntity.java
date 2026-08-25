package com.renaser.os.calendar.infrastructure.adapter.out.persistence.evento;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "reglas_recordatorio_evento", schema = "renaser")
@IdClass(ReglaRecordatorioEventoId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReglaRecordatorioEventoJpaEntity {

    @Id
    private UUID eventoId;

    @Id
    private Short orden;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private TipoReglaRecordatorioJpa tipoRegla;

    private Integer valorNumero;

    private LocalTime valorHora;
}
