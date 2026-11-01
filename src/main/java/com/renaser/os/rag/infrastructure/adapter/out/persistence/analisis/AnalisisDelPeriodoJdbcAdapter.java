package com.renaser.os.rag.infrastructure.adapter.out.persistence.analisis;

import com.renaser.os.rag.application.ports.out.analisis.LoadAnalisisDelPeriodoPort;
import com.renaser.os.rag.application.ports.out.analisis.SaveAnalisisDelPeriodoPort;
import com.renaser.os.rag.domain.model.analisis.AnalisisDelPeriodo;
import com.renaser.os.rag.domain.model.analisis.AnalisisDelPeriodoId;
import com.renaser.os.rag.domain.model.analisis.EstadoDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TextoDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code analisis_80_20} (V153) con {@link JdbcClient}. El único camino de escritura es {@link #guardar}: inserta, o actualiza la fila
 * mientras siga {@code PENDIENTE} (cláusula {@code WHERE} del {@code ON CONFLICT}), y nunca pisa un resultado. Así dos instancias que
 * lleguen a la vez al mismo periodo no se pisan: gana la primera y la otra ve {@code false}.
 *
 * <p>Lo que se guarda es texto sobre la vida de una persona: nada de esto se loguea.
 */
@Component
class AnalisisDelPeriodoJdbcAdapter implements LoadAnalisisDelPeriodoPort, SaveAnalisisDelPeriodoPort {

    private static final String COLUMNAS = """
            id, participante_id, formacion, periodo, numero, inicio, fin, estado, intentos, ultimo_intento_en, generado_en,
            creado_en, lo_que_mas_movio, lo_que_se_te_fue, como_estuviste, to_jsonb(proximos_pasos)::text AS pasos,
            cifras::text AS cifras_json
            """;
    private static final String DE_LA_PERSONA = "SELECT " + COLUMNAS + """
            FROM renaser.analisis_80_20 WHERE participante_id = :persona AND periodo = :periodo AND inicio = :inicio
            """;
    private static final String ESTADOS = """
            SELECT participante_id, periodo, inicio, estado, ultimo_intento_en
            FROM renaser.analisis_80_20 WHERE participante_id IN (:personas) AND inicio >= :desde
            """;
    private static final String ULTIMO_LISTO = "SELECT " + COLUMNAS + """
            FROM renaser.analisis_80_20
            WHERE participante_id = :persona AND formacion = :formacion AND periodo = :periodo AND estado = 'LISTO'
            ORDER BY inicio DESC LIMIT 1
            """;
    private static final String INTENTOS = "SELECT count(*) FROM renaser.analisis_80_20 WHERE ultimo_intento_en >= :desde";
    private static final String GUARDAR = """
            INSERT INTO renaser.analisis_80_20 (id, participante_id, formacion, periodo, numero, inicio, fin, estado, intentos,
                   ultimo_intento_en, generado_en, creado_en, lo_que_mas_movio, lo_que_se_te_fue, como_estuviste, proximos_pasos, cifras)
            VALUES (:id, :persona, :formacion, :periodo, :numero, :inicio, :fin, :estado, :intentos, :ultimoIntento, :generadoEn,
                   :creadoEn, :movio, :fue, :estuvo,
                   CASE WHEN CAST(:pasos AS text) IS NULL THEN NULL ELSE
                        ARRAY(SELECT e.t FROM jsonb_array_elements_text(CAST(:pasos AS jsonb)) WITH ORDINALITY AS e(t, n) ORDER BY e.n)
                   END,
                   CAST(:cifras AS jsonb))
            ON CONFLICT (participante_id, periodo, inicio) DO UPDATE SET
                   estado = EXCLUDED.estado, intentos = EXCLUDED.intentos, ultimo_intento_en = EXCLUDED.ultimo_intento_en,
                   generado_en = EXCLUDED.generado_en, lo_que_mas_movio = EXCLUDED.lo_que_mas_movio,
                   lo_que_se_te_fue = EXCLUDED.lo_que_se_te_fue, como_estuviste = EXCLUDED.como_estuviste,
                   proximos_pasos = EXCLUDED.proximos_pasos, cifras = EXCLUDED.cifras
                   WHERE renaser.analisis_80_20.estado = 'PENDIENTE'
            """;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> LISTA_DE_TEXTOS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;

    AnalisisDelPeriodoJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AnalisisDelPeriodo> deLaPersona(UserId participante, TipoDePeriodo tipo, LocalDate inicio) {
        return jdbc.sql(DE_LA_PERSONA).param("persona", participante.value()).param("periodo", tipo.name()).param("inicio", inicio)
                .query((rs, fila) -> aDominio(rs)).optional();
    }

    @Override
    public List<EstadoDeUnPeriodo> estadosDe(Collection<UserId> participantes, LocalDate inicioMinimo) {
        if (participantes.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(ESTADOS).param("personas", participantes.stream().map(UserId::value).distinct().toList())
                .param("desde", inicioMinimo)
                .query((rs, fila) -> new EstadoDeUnPeriodo(UserId.of(rs.getObject("participante_id", UUID.class)),
                        TipoDePeriodo.valueOf(rs.getString("periodo")), rs.getObject("inicio", LocalDate.class),
                        EstadoDelAnalisis.valueOf(rs.getString("estado")), instante(rs, "ultimo_intento_en")))
                .list();
    }

    @Override
    public Optional<AnalisisDelPeriodo> ultimoListo(UserId participante, Formacion formacion, TipoDePeriodo tipo) {
        return jdbc.sql(ULTIMO_LISTO).param("persona", participante.value()).param("formacion", formacion.codigo())
                .param("periodo", tipo.name()).query((rs, fila) -> aDominio(rs)).optional();
    }

    @Override
    public int intentosDesde(Instant desde) {
        Integer total = jdbc.sql(INTENTOS).param("desde", Timestamp.from(desde)).query(Integer.class).single();
        return total == null ? 0 : total;
    }

    @Override
    public boolean guardar(AnalisisDelPeriodo analisis) {
        TextoDelAnalisis texto = analisis.texto();
        return jdbc.sql(GUARDAR)
                .param("id", analisis.id().value())
                .param("persona", analisis.participanteId().value())
                .param("formacion", analisis.formacion().codigo())
                .param("periodo", analisis.tipo().name())
                .param("numero", analisis.numero())
                .param("inicio", analisis.inicio())
                .param("fin", analisis.fin())
                .param("estado", analisis.estado().name())
                .param("intentos", analisis.intentos())
                .param("ultimoIntento", marca(analisis.ultimoIntentoEn()))
                .param("generadoEn", marca(analisis.generadoEn()))
                .param("creadoEn", Timestamp.from(analisis.creadoEn()))
                .param("movio", texto == null ? null : texto.loQueMasMovio())
                .param("fue", texto == null ? null : texto.loQueSeTeFue())
                .param("estuvo", texto == null ? null : texto.comoEstuviste())
                .param("pasos", texto == null ? null : JSON.writeValueAsString(texto.proximosPasos()))
                .param("cifras", analisis.cifrasEnJson())
                .update() == 1;
    }

    private static AnalisisDelPeriodo aDominio(ResultSet rs) throws SQLException {
        String movio = rs.getString("lo_que_mas_movio");
        TextoDelAnalisis texto = movio == null ? null : new TextoDelAnalisis(movio, rs.getString("lo_que_se_te_fue"),
                rs.getString("como_estuviste"), JSON.readValue(rs.getString("pasos"), LISTA_DE_TEXTOS));
        return AnalisisDelPeriodo.rehydrate(AnalisisDelPeriodoId.of(rs.getObject("id", UUID.class)),
                UserId.of(rs.getObject("participante_id", UUID.class)), Formacion.desde(rs.getString("formacion")),
                TipoDePeriodo.valueOf(rs.getString("periodo")), rs.getInt("numero"), rs.getObject("inicio", LocalDate.class),
                rs.getObject("fin", LocalDate.class), EstadoDelAnalisis.valueOf(rs.getString("estado")), rs.getInt("intentos"),
                instante(rs, "ultimo_intento_en"), instante(rs, "generado_en"), instante(rs, "creado_en"), texto,
                rs.getString("cifras_json"));
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        Timestamp marca = rs.getTimestamp(columna);
        return marca == null ? null : marca.toInstant();
    }

    private static Timestamp marca(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }
}
