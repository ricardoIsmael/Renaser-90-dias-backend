package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.points.api.ConteoDelDia;
import com.renaser.os.points.api.ConteoDiarioHabitosFinder;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-169 de punta a punta contra Postgres real: {@code users} deriva el dia de programa de las
 * fechas en la zona del participante (con {@code dias_ajuste_programa}), {@code habits} genera el
 * dia con el catalogo REAL de las migraciones —donde POST DIARIO EN COMUNIDAD trae
 * {@code obligatorio_en_intoxicacion = true} desde produccion (V4)— y el conteo que lee el semaforo
 * ({@link ConteoDiarioHabitosFinder}) deja afuera lo opcional sin cumplir.
 *
 * <p><b>Reloj a las 03:00 UTC del domingo 20</b> (regla 02 §3): en Lima es el sabado 19 a las 22:00.
 * Cada caso esta armado para que la fecha equivocada (la UTC), la columna materializada vieja o
 * ignorar el ajuste den la respuesta contraria a la correcta.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, CicloIntoxicacionGeneracionIT.RelojTresAmUtc.class})
@Transactional
class CicloIntoxicacionGeneracionIT {

    private static final Instant TRES_AM_UTC = Instant.parse("2026-09-20T03:00:00Z");
    private static final LocalDate HOY_EN_LIMA = LocalDate.of(2026, 9, 19);
    /** POST DIARIO EN COMUNIDAD, id de produccion preservado (V4, V24). */
    private static final UUID POST_DIARIO = UUID.fromString("830c3d76-888a-4aef-bb30-fb0f0cc7ca73");

    @TestConfiguration
    static class RelojTresAmUtc {
        @Bean
        @Primary
        Clock relojTresAmUtc() {
            return FixedClock.at(TRES_AM_UTC);
        }
    }

    @Autowired
    private GenerarTracksDelDiaUseCase generarTracks;
    @Autowired
    private ConteoDiarioHabitosFinder conteoDelSemaforo;
    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("dia 10 en Lima (en UTC ya seria el 11): intoxicacion, solo el post sigue exigible")
    void elDiaDiezEnLimaEsDeIntoxicacionAunqueEnUtcYaSeaElOnce() {
        UserId participante = aprendiz(LocalDate.of(2026, 9, 10), 0, 10, HOY_EN_LIMA);

        List<Registro> registros = generarHoy(participante);

        assertThat(registros).allSatisfy(r -> assertThat(r.diaPrograma()).isEqualTo(10));
        assertSoloElPostEsExigible(registros);
        assertThat(conteoDeHoy(participante).programados()).isEqualTo(1);
    }

    @Test
    @DisplayName("dia 7 en Lima (en UTC ya seria el 8): dia normal, cada habito como su catalogo")
    void elDiaSieteEnLimaEsNormalAunqueEnUtcYaSeaElOcho() {
        UserId participante = aprendiz(LocalDate.of(2026, 9, 13), 0, 7, HOY_EN_LIMA);

        List<Registro> registros = generarHoy(participante);

        assertThat(registros).allSatisfy(r -> assertThat(r.diaPrograma()).isEqualTo(7));
        assertCadaUnoComoSuCatalogo(registros);
        assertThat(conteoDeHoy(participante).programados()).isEqualTo(exigiblesDeCatalogo(registros));
    }

    @Test
    @DisplayName("ajuste +2: el calendario diria dia 12, pero el aprendiz esta en el 10 (intoxicacion)")
    void unAjustePositivoLoDevuelveAUnDiaDeIntoxicacion() {
        UserId participante = aprendiz(LocalDate.of(2026, 9, 8), 2, 10, HOY_EN_LIMA);

        List<Registro> registros = generarHoy(participante);

        assertThat(registros).allSatisfy(r -> assertThat(r.diaPrograma()).isEqualTo(10));
        assertSoloElPostEsExigible(registros);
    }

    /**
     * Sin ajuste el calendario diria dia 9 (intoxicacion) y la columna, materializada ayer, dice 10
     * (intoxicacion); el dia de hoy, derivado, es el 11. Si alguien vuelve a leer la columna o
     * ignora el ajuste, este caso sale opcional y falla.
     */
    @Test
    @DisplayName("ajuste -2 y columna de ayer: el aprendiz ya esta en el 11, dia normal")
    void unAjusteNegativoLoSacaDelCicloAunqueLaColumnaSigaEnElDiez() {
        UserId participante = aprendiz(LocalDate.of(2026, 9, 11), -2, 10, HOY_EN_LIMA.minusDays(1));

        List<Registro> registros = generarHoy(participante);

        assertThat(registros).allSatisfy(r -> assertThat(r.diaPrograma()).isEqualTo(11));
        assertCadaUnoComoSuCatalogo(registros);
    }

    private List<Registro> generarHoy(UserId participante) {
        generarTracks.generarDiaCompletoEnSuZona(participante);
        List<Registro> registros = jdbcClient.sql("""
                        SELECT r.habito_id, r.fecha_ejecucion, r.dia_programa, r.es_opcional,
                               h.es_opcional AS opcional_de_catalogo
                        FROM renaser.registros_habito r
                        JOIN renaser.habitos h ON h.id = r.habito_id
                        WHERE r.participante_id = :pid
                        """)
                .param("pid", participante.value())
                .query((rs, fila) -> new Registro(rs.getObject("habito_id", UUID.class),
                        rs.getObject("fecha_ejecucion", LocalDate.class), rs.getInt("dia_programa"),
                        rs.getBoolean("es_opcional"), rs.getBoolean("opcional_de_catalogo")))
                .list();
        // Sin esto las aserciones de abajo pasarian con una lista vacia.
        assertThat(registros).hasSizeGreaterThan(5).anySatisfy(r -> assertThat(r.habitoId()).isEqualTo(POST_DIARIO));
        assertThat(registros).allSatisfy(r -> assertThat(r.fechaEjecucion()).isEqualTo(HOY_EN_LIMA));
        return registros;
    }

    private static void assertSoloElPostEsExigible(List<Registro> registros) {
        assertThat(registros).allSatisfy(r -> assertThat(r.esOpcional())
                .as("es_opcional de %s", r.habitoId()).isEqualTo(!r.habitoId().equals(POST_DIARIO)));
    }

    private static void assertCadaUnoComoSuCatalogo(List<Registro> registros) {
        assertThat(registros).allSatisfy(r -> assertThat(r.esOpcional())
                .as("es_opcional de %s", r.habitoId()).isEqualTo(r.opcionalDeCatalogo()));
        assertThat(registros).anySatisfy(r -> assertThat(r.esOpcional()).isFalse());
    }

    private static long exigiblesDeCatalogo(List<Registro> registros) {
        return registros.stream().filter(r -> !r.opcionalDeCatalogo()).count();
    }

    private ConteoDelDia conteoDeHoy(UserId participante) {
        List<ConteoDelDia> dias = conteoDelSemaforo.porParticipanteEntre(List.of(participante), HOY_EN_LIMA,
                HOY_EN_LIMA).get(participante);
        assertThat(dias).singleElement().satisfies(dia -> assertThat(dia.cumplidos()).isZero());
        return dias.getFirst();
    }

    /**
     * Fila coherente (regla 03, fixtures): {@code dia_programa} es la materializacion del dia
     * {@code materializadoEl}, la misma que habria dejado el barrido del reloj ese dia, y el
     * programa se activo la vispera del Dia 1 (D-66: el Dia 1 es manana, +2 o +3).
     */
    private UserId aprendiz(LocalDate fechaInicio, int diasAjuste, int diaMaterializado,
                            LocalDate materializadoEl) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Aprendiz Intoxicacion', 'APRENDIZ', 'ACTIVO')
                        """)
                .param("id", id).param("email", id + "@renaser.test").update();
        jdbcClient.sql("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, dia_programa_avanzado_el,
                                    fecha_inicio, programa_activado_en, timezone, dias_ajuste_programa)
                        VALUES (:id, :dia, :materializadoEl, :inicio, :activado, 'America/Lima', :ajuste)
                        """)
                .param("id", id).param("dia", diaMaterializado).param("materializadoEl", materializadoEl)
                .param("inicio", fechaInicio)
                .param("activado", Timestamp.from(fechaInicio.minusDays(1).atTime(15, 0).toInstant(ZoneOffset.UTC)))
                .param("ajuste", diasAjuste).update();
        return UserId.of(id);
    }

    private record Registro(UUID habitoId, LocalDate fechaEjecucion, int diaPrograma, boolean esOpcional,
                            boolean opcionalDeCatalogo) {
    }
}
