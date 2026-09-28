package com.renaser.os.chat.infrastructure.adapter.out.persistence.bienvenida;

import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaPort;
import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code cambios_bienvenida} (V73) con {@link JdbcClient}: un INSERT y dos lecturas de una línea no
 * justifican una entidad JPA (mismo criterio que {@code MarcaDeBienvenidaJdbcAdapter}).
 *
 * <p>El texto y la ruta de la portada van en columnas separadas: así la base puede exigir, fila por
 * fila, que un texto no traiga ruta, que la portada no traiga texto y el largo de cada uno. Las dos en
 * {@code NULL} = volvió al original. «El último» es el de {@code id} más alto (identidad creciente), no
 * el de {@code cambiado_en}: dos cambios en el mismo instante tendrían un orden ambiguo.
 */
@Component
class CambiosDeBienvenidaJdbcAdapter implements CambiosDeBienvenidaPort {

    private static final String REGISTRAR = """
            INSERT INTO renaser.cambios_bienvenida (pieza, texto, portada_ruta, cambiado_por, cambiado_en)
            VALUES (:pieza, :texto, :ruta, :por, :en)
            """;
    private static final String ULTIMOS = """
            SELECT DISTINCT ON (pieza) pieza, texto, portada_ruta, cambiado_por, cambiado_en
              FROM renaser.cambios_bienvenida
             ORDER BY pieza, id DESC
            """;
    private static final String ULTIMO = """
            SELECT pieza, texto, portada_ruta, cambiado_por, cambiado_en
              FROM renaser.cambios_bienvenida
             WHERE pieza = :pieza
             ORDER BY id DESC
             LIMIT 1
            """;

    private final JdbcClient jdbcClient;

    CambiosDeBienvenidaJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void registrar(CambioDeBienvenida cambio) {
        boolean esPortada = !cambio.pieza().esTexto();
        jdbcClient.sql(REGISTRAR)
                .param("pieza", cambio.pieza().name())
                .param("texto", esPortada ? null : cambio.valor(), Types.VARCHAR)
                .param("ruta", esPortada ? cambio.valor() : null, Types.VARCHAR)
                .param("por", cambio.quien().map(UserId::value).orElse(null), Types.OTHER)
                .param("en", Timestamp.from(cambio.cambiadoEn()))
                .update();
    }

    @Override
    public Map<PiezaDeBienvenida, CambioDeBienvenida> ultimos() {
        Map<PiezaDeBienvenida, CambioDeBienvenida> ultimos = new EnumMap<>(PiezaDeBienvenida.class);
        jdbcClient.sql(ULTIMOS).query((rs, n) -> cambio(rs)).list().forEach(c -> ultimos.put(c.pieza(), c));
        return ultimos;
    }

    @Override
    public Optional<CambioDeBienvenida> ultimo(PiezaDeBienvenida pieza) {
        return jdbcClient.sql(ULTIMO).param("pieza", pieza.name()).query((rs, n) -> cambio(rs)).optional();
    }

    /** Rehidrata sin volver a validar: lo que la base ya aceptó se sigue leyendo aunque cambien las reglas. */
    private static CambioDeBienvenida cambio(ResultSet rs) throws SQLException {
        PiezaDeBienvenida pieza = PiezaDeBienvenida.valueOf(rs.getString("pieza"));
        String valor = pieza.esTexto() ? rs.getString("texto") : rs.getString("portada_ruta");
        UUID por = rs.getObject("cambiado_por", UUID.class);
        return new CambioDeBienvenida(pieza, valor, por == null ? null : UserId.of(por),
                rs.getTimestamp("cambiado_en").toInstant());
    }
}
