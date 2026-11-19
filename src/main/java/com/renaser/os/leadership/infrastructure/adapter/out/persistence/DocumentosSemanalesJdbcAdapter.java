package com.renaser.os.leadership.infrastructure.adapter.out.persistence;

import com.renaser.os.leadership.application.ports.out.GuardarDocumentoSemanalPort;
import com.renaser.os.leadership.application.ports.out.LeerDocumentoSemanalPort;
import com.renaser.os.leadership.domain.model.documento.DescargaDeDocumento;
import com.renaser.os.leadership.domain.model.documento.DocumentoSemanal;
import com.renaser.os.leadership.domain.model.documento.DocumentoSemanal.Constancia;
import com.renaser.os.leadership.domain.model.documento.DocumentoSemanal.Contenido;
import com.renaser.os.leadership.domain.model.documento.TipoDeDocumento;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code documentos_semanales} y {@code descargas_de_documentos} (V199) con {@link JdbcClient}: solo INSERT y lectura, porque los documentos se
 * guardan para siempre (spec 056, FR-040). Sin entidad JPA: es una fila de ids, fechas y una huella, y no hay un agregado que cargar y
 * cambiar.
 *
 * <p><b>Dos pedidos a la vez.</b> El único {@code (persona_id, tipo, periodo_inicio, huella)} decide: {@code ON CONFLICT DO NOTHING} deja pasar
 * a uno y el otro, sin error, lee la fila que ya quedó —cada sentencia va en su propia transacción, así que lo que el primero confirmó ya se
 * ve—. Los dos terminan con el mismo documento, que es lo que pide la regla de los pedidos repetidos.
 */
@Component
class DocumentosSemanalesJdbcAdapter implements LeerDocumentoSemanalPort, GuardarDocumentoSemanalPort {

    private static final String INSERTAR = """
            INSERT INTO renaser.documentos_semanales
                (id, persona_id, tipo, periodo_inicio, periodo_fin, huella, ruta, generado_en, generado_por)
            VALUES (:id, :persona, :tipo, :inicio, :fin, :huella, :ruta, :generadoEn, :generadoPor)
            ON CONFLICT (persona_id, tipo, periodo_inicio, huella) DO NOTHING
            """;

    private static final String BUSCAR = """
            SELECT id, persona_id, tipo, periodo_inicio, periodo_fin, huella, ruta, generado_en, generado_por
              FROM renaser.documentos_semanales
             WHERE persona_id = :persona AND tipo = :tipo AND periodo_inicio = :inicio AND huella = :huella
            """;

    private static final String REGISTRAR_DESCARGA = """
            INSERT INTO renaser.descargas_de_documentos (id, documento_id, actor_id, reutilizado, pedido_en)
            VALUES (:id, :documento, :actor, :reutilizado, :pedidoEn)
            """;

    private final JdbcClient jdbcClient;

    DocumentosSemanalesJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<DocumentoSemanal> existente(Buscado buscado) {
        return jdbcClient.sql(BUSCAR)
                .param("persona", buscado.personaId().value())
                .param("tipo", buscado.tipo().name())
                .param("inicio", buscado.periodoInicio())
                .param("huella", buscado.huella())
                .query(DocumentosSemanalesJdbcAdapter::aDocumento)
                .optional();
    }

    @Override
    public DocumentoSemanal insertar(DocumentoSemanal documento) {
        jdbcClient.sql(INSERTAR)
                .param("id", documento.id())
                .param("persona", documento.personaId().value())
                .param("tipo", documento.tipo().name())
                .param("inicio", documento.periodoInicio())
                .param("fin", documento.periodoFin())
                .param("huella", documento.huella())
                .param("ruta", documento.ruta())
                .param("generadoEn", Timestamp.from(documento.generadoEn()))
                .param("generadoPor", documento.generadoPor().value())
                .update();
        return existente(new Buscado(documento.personaId(), documento.tipo(), documento.periodoInicio(), documento.huella()))
                .orElseThrow(() -> new IllegalStateException("El documento recién guardado no se encuentra: " + documento.ruta()));
    }

    @Override
    public void registrarDescarga(DescargaDeDocumento descarga) {
        jdbcClient.sql(REGISTRAR_DESCARGA)
                .param("id", descarga.id())
                .param("documento", descarga.documentoId())
                .param("actor", descarga.actorId().value())
                .param("reutilizado", descarga.reutilizado())
                .param("pedidoEn", Timestamp.from(descarga.pedidoEn()))
                .update();
    }

    private static DocumentoSemanal aDocumento(ResultSet fila, int numero) throws SQLException {
        Contenido contenido = new Contenido(UserId.of(fila.getObject("persona_id", UUID.class)),
                TipoDeDocumento.valueOf(fila.getString("tipo")), fila.getObject("periodo_inicio", java.time.LocalDate.class),
                fila.getObject("periodo_fin", java.time.LocalDate.class), fila.getString("huella"),
                UserId.of(fila.getObject("generado_por", UUID.class)));
        return DocumentoSemanal.rehydrate(fila.getObject("id", UUID.class), contenido,
                new Constancia(fila.getString("ruta"), fila.getTimestamp("generado_en").toInstant()));
    }
}
