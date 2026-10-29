package com.renaser.os.mentoring.infrastructure.adapter.out.persistence.huecos;

import com.renaser.os.mentoring.application.ports.out.FotosDeHuecosPort;
import com.renaser.os.mentoring.domain.model.huecos.DiaDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.FotoDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.HuecoQueSeRepite;
import com.renaser.os.mentoring.domain.model.huecos.LoReal;
import com.renaser.os.mentoring.domain.model.huecos.SemanaDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.VentanaDelDia;
import com.renaser.os.shared.domain.TramoDelDia;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code huecos_semana} (V120) con {@link JdbcClient}. APPEND-ONLY: el único INSERT usa {@code ON CONFLICT DO NOTHING}
 * y devuelve si de verdad insertó, así que una segunda corrida del barrido (o una segunda instancia) nunca reescribe la
 * foto. No hay UPDATE ni DELETE desde el código.
 *
 * <p>La foto se guarda como se calculó: los siete días con su ventana, lo ocupado y los huecos (jsonb) y los totales en
 * columnas, con las horas en minutos del día. Mapper a mano entre el dominio y esa forma: si la regla de los huecos
 * cambia mañana, lo que se lee es lo que se mostró entonces.
 */
@Component
class FotosDeHuecosJdbcAdapter implements FotosDeHuecosPort {

    private static final String LEER = """
            SELECT semana_inicio, zona, cumplidos_a_tiempo, cumplidos_tarde, no_cumplidos,
                   huecos_que_se_repiten::text AS repetidos, horario_que_regia::text AS dias, cerrada_en
            FROM renaser.huecos_semana
            WHERE participante_id = :participante AND semana_inicio = :lunes
            """;
    private static final String CON_FOTO = """
            SELECT participante_id FROM renaser.huecos_semana
            WHERE participante_id IN (:participantes) AND semana_inicio = :lunes
            """;
    private static final String REGISTRAR = """
            INSERT INTO renaser.huecos_semana (participante_id, semana_inicio, zona, minutos_ocupados, minutos_libres,
                   cumplidos_a_tiempo, cumplidos_tarde, no_cumplidos, huecos_que_se_repiten, horario_que_regia,
                   cerrada_en)
            VALUES (:participante, :lunes, :zona, :ocupados, :libres, :aTiempo, :tarde, :noCumplidos,
                   CAST(:repetidos AS jsonb), CAST(:dias AS jsonb), :cerradaEn)
            ON CONFLICT (participante_id, semana_inicio) DO NOTHING
            """;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<DiaJson>> DIAS = new TypeReference<>() {
    };
    private static final TypeReference<List<RepetidoJson>> REPETIDOS = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;

    FotosDeHuecosJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<FotoDeHuecos> deLaSemana(UserId participante, LocalDate lunes) {
        return jdbcClient.sql(LEER).param("participante", participante.value()).param("lunes", lunes)
                .query((rs, fila) -> foto(participante, lunes, rs)).optional();
    }

    @Override
    public Set<UserId> conFotoDe(Collection<UserId> participantes, LocalDate lunes) {
        if (participantes.isEmpty()) {
            return Set.of();
        }
        Set<UserId> conFoto = new HashSet<>();
        jdbcClient.sql(CON_FOTO).param("participantes", participantes.stream().map(UserId::value).distinct().toList())
                .param("lunes", lunes)
                .query((rs, fila) -> conFoto.add(UserId.of(rs.getObject("participante_id", UUID.class))))
                .list();
        return conFoto;
    }

    @Override
    public boolean registrar(FotoDeHuecos foto) {
        SemanaDeHuecos semana = foto.semana();
        return jdbcClient.sql(REGISTRAR)
                .param("participante", foto.participante().value())
                .param("lunes", semana.lunes())
                .param("zona", foto.zona().getId())
                .param("ocupados", semana.minutosOcupados())
                .param("libres", semana.minutosLibres())
                .param("aTiempo", semana.real().aTiempo())
                .param("tarde", semana.real().tarde())
                .param("noCumplidos", semana.real().noCumplidos())
                .param("repetidos", JSON.writeValueAsString(semana.repetidos().stream().map(RepetidoJson::de).toList()))
                .param("dias", JSON.writeValueAsString(semana.dias().stream().map(DiaJson::de).toList()))
                .param("cerradaEn", Timestamp.from(foto.cerradaEn()))
                .update() == 1;
    }

    private static FotoDeHuecos foto(UserId participante, LocalDate lunes, ResultSet rs) throws SQLException {
        List<DiaDeHuecos> dias = JSON.readValue(rs.getString("dias"), DIAS).stream().map(DiaJson::aDominio).toList();
        List<HuecoQueSeRepite> repetidos = JSON.readValue(rs.getString("repetidos"), REPETIDOS).stream()
                .map(RepetidoJson::aDominio).toList();
        LoReal real = new LoReal(rs.getInt("cumplidos_a_tiempo"), rs.getInt("cumplidos_tarde"),
                rs.getInt("no_cumplidos"));
        return new FotoDeHuecos(participante, ZoneId.of(rs.getString("zona")),
                new SemanaDeHuecos(lunes, dias, repetidos, real), rs.getTimestamp("cerrada_en").toInstant());
    }

    /** Un tramo en minutos del día. */
    record TramoJson(int desde, int hasta) {

        static TramoJson de(TramoDelDia tramo) {
            return new TramoJson(tramo.desde(), tramo.hasta());
        }

        TramoDelDia aDominio() {
            return new TramoDelDia(desde, hasta);
        }
    }

    /** Un día de la foto. {@code fecha} va como texto ISO: no depende de cómo el mapper escriba las fechas. */
    record DiaJson(String fecha, TramoJson ventana, List<TramoJson> ocupados, List<TramoJson> huecos,
                   boolean conPlan) {

        static DiaJson de(DiaDeHuecos dia) {
            return new DiaJson(dia.fecha().toString(), new TramoJson(dia.ventana().desde(), dia.ventana().hasta()),
                    dia.ocupados().stream().map(TramoJson::de).toList(),
                    dia.huecos().stream().map(TramoJson::de).toList(), dia.conPlan());
        }

        DiaDeHuecos aDominio() {
            return new DiaDeHuecos(LocalDate.parse(fecha), new VentanaDelDia(ventana.desde(), ventana.hasta()),
                    ocupados.stream().map(TramoJson::aDominio).toList(),
                    huecos.stream().map(TramoJson::aDominio).toList(), conPlan);
        }
    }

    /** Un tramo que se repite, con los días ISO en que está libre. */
    record RepetidoJson(int desde, int hasta, List<Integer> dias) {

        static RepetidoJson de(HuecoQueSeRepite repetido) {
            return new RepetidoJson(repetido.tramo().desde(), repetido.tramo().hasta(), repetido.dias());
        }

        HuecoQueSeRepite aDominio() {
            return new HuecoQueSeRepite(new TramoDelDia(desde, hasta), dias);
        }
    }
}
