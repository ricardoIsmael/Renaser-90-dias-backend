package com.renaser.os.plataforma.infrastructure.adapter.out.persistence;

import com.renaser.os.plataforma.application.ports.out.MetricasDeDesplieguePort;
import com.renaser.os.plataforma.domain.model.despliegue.ConteoDeDespliegue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * {@code funcionalidades_metricas_hora} (V114). El lote de cada minuto va en UNA sentencia
 * ({@code INSERT … VALUES (…), (…) ON CONFLICT DO UPDATE} sumando): o entra entero o no entra nada, y si falla el lote
 * vuelve a la memoria sin contar dos veces lo que ya se había escrito. Las celdas del lote son distintas entre sí (salen
 * de un mapa), así que la sentencia nunca toca dos veces la misma fila.
 */
@Component
class MetricasDeDespliegueJdbcAdapter implements MetricasDeDesplieguePort {

    private static final String SUMAR = """
            INSERT INTO renaser.funcionalidades_metricas_hora (clave, hora, dentro, pedidos, errores)
            VALUES %s
            ON CONFLICT (clave, hora, dentro) DO UPDATE
               SET pedidos = funcionalidades_metricas_hora.pedidos + EXCLUDED.pedidos,
                   errores = funcionalidades_metricas_hora.errores + EXCLUDED.errores
            """;
    private static final String DESDE = """
            SELECT clave, hora, dentro, pedidos, errores
              FROM renaser.funcionalidades_metricas_hora
             WHERE clave = :clave AND hora >= :desde
             ORDER BY hora, dentro
            """;
    private static final String BORRAR = "DELETE FROM renaser.funcionalidades_metricas_hora WHERE hora < :limite";

    private final JdbcClient jdbc;

    MetricasDeDespliegueJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void sumar(Collection<ConteoDeDespliegue> conteos) {
        if (conteos.isEmpty()) {
            return;
        }
        StringJoiner valores = new StringJoiner(", ");
        Map<String, Object> parametros = new LinkedHashMap<>();
        int i = 0;
        for (ConteoDeDespliegue conteo : conteos) {
            valores.add("(:c%1$d, :h%1$d, :d%1$d, :p%1$d, :e%1$d)".formatted(i));
            parametros.put("c" + i, conteo.clave());
            parametros.put("h" + i, Timestamp.from(conteo.hora()));
            parametros.put("d" + i, conteo.dentro());
            parametros.put("p" + i, conteo.pedidos());
            parametros.put("e" + i, conteo.errores());
            i++;
        }
        jdbc.sql(SUMAR.formatted(valores)).params(parametros).update();
    }

    @Override
    public List<ConteoDeDespliegue> desde(String clave, Instant hora) {
        return jdbc.sql(DESDE).param("clave", clave).param("desde", Timestamp.from(hora))
                .query((rs, n) -> new ConteoDeDespliegue(rs.getString("clave"), rs.getTimestamp("hora").toInstant(),
                        rs.getBoolean("dentro"), rs.getLong("pedidos"), rs.getLong("errores")))
                .list();
    }

    @Override
    public int borrarAnterioresA(Instant limite) {
        return jdbc.sql(BORRAR).param("limite", Timestamp.from(limite)).update();
    }
}
