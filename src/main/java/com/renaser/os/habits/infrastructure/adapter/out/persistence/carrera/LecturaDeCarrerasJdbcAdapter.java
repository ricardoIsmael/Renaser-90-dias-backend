package com.renaser.os.habits.infrastructure.adapter.out.persistence.carrera;

import com.renaser.os.habits.application.ports.out.carrera.LeerCarrerasPort;
import com.renaser.os.habits.application.ports.out.carrera.RetencionDeRutasPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Las lecturas de {@code carreras} (V170) para la agenda y las fichas, y el borrado de las rutas viejas (spec 042). Las dos primeras
 * se apoyan en {@code carreras_participante_fecha_idx}; el borrado, en el índice parcial {@code carreras_con_ruta_idx}.
 */
@Component
class LecturaDeCarrerasJdbcAdapter implements LeerCarrerasPort, RetencionDeRutasPort {

    private static final String DEL_DIA = """
            SELECT COUNT(*) AS cantidad,
                   COALESCE(SUM(distancia_m) FILTER (WHERE cuenta_para_el_habito), 0) / 1000 AS km
              FROM renaser.carreras
             WHERE participante_id = :participante AND fecha = :fecha
            """;

    private static final String ENTRE = """
            SELECT participante_id, fecha, carrera_id, distancia_m, duracion_activa_seg, por_revisar, cuenta_para_el_habito,
                   ruta_polyline IS NOT NULL AS tiene_ruta
              FROM renaser.carreras
             WHERE participante_id IN (:participantes) AND fecha BETWEEN :desde AND :hasta
             ORDER BY participante_id, iniciada_en
            """;

    private static final String BORRAR_RUTAS = """
            UPDATE renaser.carreras SET ruta_polyline = NULL, ruta_puntos = NULL
             WHERE id IN (SELECT id FROM renaser.carreras
                           WHERE ruta_polyline IS NOT NULL AND iniciada_en < :limite
                           ORDER BY iniciada_en LIMIT :lote)
            """;

    private final JdbcClient jdbc;

    LecturaDeCarrerasJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CarrerasDelDia delDia(UserId participante, LocalDate fecha) {
        return jdbc.sql(DEL_DIA).param("participante", participante.value()).param("fecha", fecha)
                .query((rs, fila) -> new CarrerasDelDia(rs.getInt("cantidad"), km(rs.getBigDecimal("km"))))
                .single();
    }

    @Override
    public List<ResumenDeCarrera> entre(Collection<UserId> participantes, LocalDate desde, LocalDate hasta) {
        if (participantes.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = participantes.stream().map(UserId::value).distinct().toList();
        return jdbc.sql(ENTRE).param("participantes", ids).param("desde", desde).param("hasta", hasta)
                .query((rs, fila) -> {
                    BigDecimal metros = rs.getBigDecimal("distancia_m");
                    int duracion = rs.getInt("duracion_activa_seg");
                    return new ResumenDeCarrera(UserId.of(rs.getObject("participante_id", UUID.class)),
                            rs.getObject("fecha", LocalDate.class), rs.getObject("carrera_id", UUID.class),
                            km(metros.divide(new BigDecimal(1000), 4, RoundingMode.HALF_UP)), duracion,
                            (int) Math.round(duracion / (metros.doubleValue() / 1000)), rs.getBoolean("por_revisar"),
                            rs.getBoolean("cuenta_para_el_habito"), rs.getBoolean("tiene_ruta"));
                })
                .list();
    }

    /** Cada lote en su propia transacción: un fallo a mitad no deshace lo ya borrado. */
    @Override
    @Transactional
    public int borrarRutasAnterioresA(Instant limite, int lote) {
        return jdbc.sql(BORRAR_RUTAS).param("limite", Timestamp.from(limite)).param("lote", lote).update();
    }

    private static BigDecimal km(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
