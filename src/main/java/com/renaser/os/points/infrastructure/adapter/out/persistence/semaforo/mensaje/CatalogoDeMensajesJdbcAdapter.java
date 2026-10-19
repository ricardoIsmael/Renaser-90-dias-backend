package com.renaser.os.points.infrastructure.adapter.out.persistence.semaforo.mensaje;

import com.renaser.os.points.domain.model.semaforo.mensaje.GrupoDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.MensajeDelSemaforo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * {@code mensajes_semaforo} con {@link JdbcClient} y mapeo a mano, igual que los otros adaptadores del semáforo. El
 * catálogo se puede corregir con SQL sin publicar nada (D-277), así que una fila con un texto mal escrito (una
 * variable que no existe, una llave suelta) no puede tumbar a las demás: se salta y se avisa en el log.
 *
 * <p>Solo sabe leer. Quién lo guarda en memoria y por cuánto lo decide {@link CatalogoDeMensajesEnMemoria}.
 */
@Component
class CatalogoDeMensajesJdbcAdapter implements LectorDelCatalogo {

    private static final Logger log = LoggerFactory.getLogger(CatalogoDeMensajesJdbcAdapter.class);

    private static final String TODOS = "SELECT id, grupo, texto, sticker, activo FROM renaser.mensajes_semaforo ORDER BY id";

    private final JdbcClient jdbcClient;

    CatalogoDeMensajesJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<MensajeDelSemaforo> todos() {
        return jdbcClient.sql(TODOS)
                .query(CatalogoDeMensajesJdbcAdapter::mensajeOSaltar)
                .list().stream().flatMap(Optional::stream).toList();
    }

    private static Optional<MensajeDelSemaforo> mensajeOSaltar(ResultSet rs, int fila) throws SQLException {
        try {
            return Optional.of(new MensajeDelSemaforo(rs.getString("id"), GrupoDelMensaje.valueOf(rs.getString("grupo")),
                    rs.getString("texto"), rs.getString("sticker"), rs.getBoolean("activo")));
        } catch (IllegalArgumentException textoInvalido) {
            log.warn("[points.CatalogoDeMensajes] mensaje {} saltado, su texto no es valido: {}",
                    rs.getString("id"), textoInvalido.getMessage());
            return Optional.empty();
        }
    }
}
