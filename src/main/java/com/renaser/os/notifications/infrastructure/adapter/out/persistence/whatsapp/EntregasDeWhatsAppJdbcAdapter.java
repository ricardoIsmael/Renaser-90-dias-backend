package com.renaser.os.notifications.infrastructure.adapter.out.persistence.whatsapp;

import com.renaser.os.notifications.application.ports.out.whatsapp.EntregasDeWhatsAppPort;
import com.renaser.os.notifications.domain.model.whatsapp.EntregaDeWhatsApp;
import com.renaser.os.notifications.domain.model.whatsapp.EstadoDeEntrega;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La tabla {@code entregas_whatsapp} (V168, spec 041, FR-030) con {@link JdbcClient}: el apartado es un {@code INSERT ... ON CONFLICT (clave) DO NOTHING}, y lo que dice si
 * la entrega entro es la base, asi que dos instancias a la vez tampoco duplican (JPA haria un {@code merge}). Sin el texto del mensaje, sin el numero y sin respuestas.
 *
 * <p><b>El apartado y el resultado van cada uno en SU transaccion</b> ({@code REQUIRES_NEW}): el listener que los llama corre dentro de otra, y si el apartado quedara
 * pendiente de ese commit, una caida entre la llamada al proveedor y el commit lo desharia y el reintento del *outbox* mandaria el mensaje dos veces. La llamada al
 * proveedor va entre los dos, fuera de toda transaccion de esta tabla.
 */
@Component
class EntregasDeWhatsAppJdbcAdapter implements EntregasDeWhatsAppPort {

    private static final String COLUMNAS = "id, clave, evento_id, usuario_id, aviso_min, estado, motivo, wamid, proveedor_id, creado_en, actualizado_en";

    private final JdbcClient jdbc;

    EntregasDeWhatsAppJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean apartar(EntregaDeWhatsApp e) {
        return jdbc.sql("""
                        INSERT INTO renaser.entregas_whatsapp (%s)
                        VALUES (:id, :clave, :evento, :usuario, :aviso, :estado, :motivo, :wamid, :proveedor, :creado, :actualizado)
                        ON CONFLICT (clave) DO NOTHING
                        """.formatted(COLUMNAS))
                .param("id", e.id()).param("clave", e.clave()).param("evento", e.eventoId()).param("usuario", e.usuarioId().value())
                .param("aviso", e.avisoMin()).param("estado", e.estado().name()).param("motivo", e.motivo())
                .param("wamid", e.wamid()).param("proveedor", e.proveedorId())
                .param("creado", Timestamp.from(e.creadoEn())).param("actualizado", Timestamp.from(e.actualizadoEn()))
                .update() == 1;
    }

    /** El estado solo avanza (un aviso del proveedor pudo llegar antes de que se guarde este); el wamid y el id del proveedor se completan siempre que falten. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void guardarResultado(EntregaDeWhatsApp e) {
        completarIdentificadores(e.id(), e.wamid(), e.proveedorId());
        avanzar(e.id(), e.estado(), e.motivo(), e.actualizadoEn());
    }

    @Override
    @Transactional
    public boolean avanzarEstado(UUID id, EstadoDeEntrega nuevo, String motivo, String wamid, String proveedorId, Instant ahora) {
        completarIdentificadores(id, wamid, proveedorId);
        return avanzar(id, nuevo, motivo, ahora);
    }

    private boolean avanzar(UUID id, EstadoDeEntrega nuevo, String motivo, Instant ahora) {
        List<String> desde = nuevo.desdeLosQuePuedeAvanzarA().stream().map(EstadoDeEntrega::name).toList();
        if (desde.isEmpty()) {
            return false;
        }
        return jdbc.sql("""
                        UPDATE renaser.entregas_whatsapp
                        SET estado = :nuevo, motivo = COALESCE(CAST(:motivo AS varchar), motivo), actualizado_en = :ahora
                        WHERE id = :id AND estado IN (:desde)
                        """)
                .param("nuevo", nuevo.name()).param("motivo", motivo).param("ahora", Timestamp.from(ahora)).param("id", id)
                .param("desde", desde).update() == 1;
    }

    private void completarIdentificadores(UUID id, String wamid, String proveedorId) {
        if (wamid == null && proveedorId == null) {
            return;
        }
        jdbc.sql("""
                        UPDATE renaser.entregas_whatsapp
                        SET wamid = COALESCE(wamid, CAST(:wamid AS text)), proveedor_id = COALESCE(proveedor_id, CAST(:proveedor AS text))
                        WHERE id = :id
                        """)
                .param("wamid", wamid).param("proveedor", proveedorId).param("id", id).update();
    }

    @Override
    public Optional<EntregaDeWhatsApp> porId(UUID id) {
        return uno("id = :valor", id);
    }

    @Override
    public Optional<EntregaDeWhatsApp> porProveedorId(String proveedorId) {
        return uno("proveedor_id = :valor", proveedorId);
    }

    @Override
    public Optional<EntregaDeWhatsApp> porWamid(String wamid) {
        return uno("wamid = :valor", wamid);
    }

    private Optional<EntregaDeWhatsApp> uno(String condicion, Object valor) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM renaser.entregas_whatsapp WHERE " + condicion + " LIMIT 1")
                .param("valor", valor).query(EntregasDeWhatsAppJdbcAdapter::fila).optional();
    }

    @Override
    public long contarQueSalieron(Instant desde, Instant hasta) {
        return jdbc.sql("""
                        SELECT count(*) FROM renaser.entregas_whatsapp
                        WHERE creado_en >= :desde AND creado_en < :hasta AND estado NOT IN ('OMITIDO', 'FALLIDO')
                        """)
                .param("desde", Timestamp.from(desde)).param("hasta", Timestamp.from(hasta)).query(Long.class).single();
    }

    @Override
    public ConteoDeEntregas conteoDelEvento(UUID eventoId) {
        Map<EstadoDeEntrega, Integer> porEstado = new EnumMap<>(EstadoDeEntrega.class);
        jdbc.sql("SELECT estado, count(*) AS n FROM renaser.entregas_whatsapp WHERE evento_id = :evento GROUP BY estado")
                .param("evento", eventoId)
                .query((rs, fila) -> {
                    porEstado.put(EstadoDeEntrega.valueOf(rs.getString("estado")), rs.getInt("n"));
                    return fila;
                }).list();
        int[] sinNumero = jdbc.sql("""
                        SELECT count(*) AS filas, count(DISTINCT usuario_id) AS personas FROM renaser.entregas_whatsapp
                        WHERE evento_id = :evento AND estado = 'OMITIDO' AND motivo = 'Sin WhatsApp'
                        """)
                .param("evento", eventoId).query((rs, fila) -> new int[] {rs.getInt("filas"), rs.getInt("personas")}).single();
        return new ConteoDeEntregas(porEstado, sinNumero[0], sinNumero[1]);
    }

    @Override
    @Transactional
    public int purgarAnterioresA(Instant limite) {
        return jdbc.sql("DELETE FROM renaser.entregas_whatsapp WHERE creado_en < :limite")
                .param("limite", Timestamp.from(limite)).update();
    }

    private static EntregaDeWhatsApp fila(ResultSet rs, int numero) throws SQLException {
        return new EntregaDeWhatsApp(rs.getObject("id", UUID.class), rs.getObject("clave", UUID.class),
                rs.getObject("evento_id", UUID.class), UserId.of(rs.getObject("usuario_id", UUID.class)), rs.getInt("aviso_min"),
                EstadoDeEntrega.valueOf(rs.getString("estado")), rs.getString("motivo"), rs.getString("wamid"),
                rs.getString("proveedor_id"), rs.getTimestamp("creado_en").toInstant(), rs.getTimestamp("actualizado_en").toInstant());
    }
}
