package com.renaser.os.points.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.points.application.ports.in.ranking.RegenerarSnapshotsRankingUseCase;
import com.renaser.os.points.application.ports.in.ranking.RegenerarSnapshotsRankingUseCase.RegenerarSnapshotsCommand;
import com.renaser.os.points.application.ports.in.ranking.RegenerarSnapshotsRankingUseCase.ResultadoRegeneracion;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingPort;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-450. Una cuenta con rol APRENDIZ y estado ACTIVO pero SIN fila en {@code participantes_programa}
 * (la invitacion con rol aprendiz de E-367) hacia fallar el corte de los cuatro rankings para TODOS:
 * {@code ranking_aprendices_participante_id_fkey}. Contra Postgres real porque lo que fallaba era la
 * FK, que ningun doble reproduce.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RankingConAprendizSinProgramaIT {

    private static final LocalDate FECHA = LocalDate.of(2026, 9, 28);

    @Autowired
    private RegenerarSnapshotsRankingUseCase regenerar;
    @Autowired
    private LoadRankingPort ranking;
    @Autowired
    private JdbcClient jdbcClient;
    @Autowired
    private CacheManager cacheManager;

    private UserId admin;
    private UserId ana;
    private UserId beto;
    private UserId sinPrograma;

    @BeforeEach
    void padron() {
        cacheManager.getCacheNames().forEach(nombre -> cacheManager.getCache(nombre).clear());
        admin = usuario("ADMIN");
        ana = conPrograma(usuario("APRENDIZ"));
        beto = conPrograma(usuario("APRENDIZ"));
        sinPrograma = usuario("APRENDIZ");
    }

    @Test
    void elCorteSaleParaLosDemasEnLosCuatroTiposYElSinProgramaQuedaAfuera() {
        ResultadoRegeneracion resultado = regenerar.regenerar(new RegenerarSnapshotsCommand(admin, FECHA));

        assertThat(resultado.fallados()).isEmpty();
        assertThat(resultado.tipos()).containsExactlyInAnyOrder("LEAGUE", "CELL", "GENERAL", "KILOMETROS");
        for (TipoRanking tipo : TipoRanking.CON_CORTE_DIARIO) {
            assertThat(ranking.porTipoYFecha(tipo, FECHA))
                    .as("corte %s", tipo)
                    .extracting(LoadRankingPort.EntradaRankingConNombre::participanteId)
                    .contains(ana, beto)
                    .doesNotContain(sinPrograma);
        }
    }

    private UserId usuario(String rol) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Ranking E450', CAST(:rol AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .param("id", id).param("email", id + "@renaser.test").param("rol", rol).update();
        return UserId.of(id);
    }

    private UserId conPrograma(UserId id) {
        jdbcClient.sql("INSERT INTO renaser.participantes_programa (usuario_id) VALUES (:id)")
                .param("id", id.value()).update();
        return id;
    }
}
