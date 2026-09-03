package com.renaser.os.points.infrastructure.adapter.out.persistence.ranking;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.renaser.os.points.application.ports.out.ranking.LoadRankingPort;
import com.renaser.os.points.application.ports.out.ranking.SaveRankingSnapshotPort;
import com.renaser.os.points.domain.model.ranking.TipoRanking;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba el {@code @Cacheable}/{@code @CacheEvict} de {@link RankingPersistenceAdapter} (D-63)
 * con un contexto Spring minimo — SIN Testcontainers, mockeando el repositorio JPA y
 * {@link UserSummaryFinder}. Hace falta un contexto Spring real (no {@code new
 * RankingPersistenceAdapter(...)}) porque el cacheo es AOP sobre el proxy del bean: instanciar
 * la clase a mano nunca lo ejercitaria.
 *
 * <p>Cubre exactamente los dos riesgos que señala CLAUDE.MD para esta tarea: que dos llamadas
 * iguales no dupliquen la consulta a la base, y que la clave de cache incluya TODOS los
 * parametros que cambian el resultado (tipo, fecha) — si a la clave le faltara alguno, este
 * test lo detectaria como una llamada "de mas" a la base que en realidad fue un cache hit
 * incorrecto sirviendo el dato equivocado.
 */
@SpringJUnitConfig(classes = RankingPersistenceAdapterCacheTest.Contexto.class)
class RankingPersistenceAdapterCacheTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 8, 24);
    private static final UUID PARTICIPANTE = UUID.randomUUID();

    // Autowireado por INTERFAZ, no por la clase concreta: @EnableCaching arma un proxy JDK
    // dinamico (proxy-target-class=false por defecto) que implementa los puertos publicos del
    // adaptador, no la clase RankingPersistenceAdapter en si — igual que en produccion, donde
    // otros modulos siempre dependen del puerto y nunca de la clase concreta.
    @Autowired
    private LoadRankingPort adapter;
    @Autowired
    private SaveRankingSnapshotPort adapterEscritura;
    @Autowired
    private SpringDataRankingAprendizRepository repository;
    @Autowired
    private UserSummaryFinder userSummaryFinder;
    @Autowired
    private CaffeineCacheManager cacheManager;

    @BeforeEach
    void prepararMocks() {
        reset(repository, userSummaryFinder);
        cacheManager.getCacheNames().forEach(nombre -> cacheManager.getCache(nombre).clear());

        RankingAprendizJpaEntity fila = new RankingAprendizJpaEntity(FECHA, TipoRankingJpa.LIGA, PARTICIPANTE, 1,
                new BigDecimal("100"));
        when(repository.findByTipoAndFechaOrderByPosicion(any(), any())).thenReturn(List.of(fila));
        when(userSummaryFinder.findByIds(any())).thenReturn(Map.of(UserId.of(PARTICIPANTE),
                new UserSummary(UserId.of(PARTICIPANTE), "Aprendiz de Prueba", null, UserRole.TRAINEE,
                        UserStatus.ACTIVE)));
    }

    @Test
    @DisplayName("dos llamadas seguidas con el mismo tipo y fecha consultan la base una sola vez")
    void mismaConsultaNoRepiteLaConsultaALaBase() {
        adapter.porTipoYFecha(TipoRanking.LEAGUE, FECHA);
        adapter.porTipoYFecha(TipoRanking.LEAGUE, FECHA);

        verify(repository, times(1)).findByTipoAndFechaOrderByPosicion(any(), any());
    }

    @Test
    @DisplayName("tipo distinto no comparte entrada de cache")
    void tipoDistintoNoComparteCache() {
        adapter.porTipoYFecha(TipoRanking.LEAGUE, FECHA);
        adapter.porTipoYFecha(TipoRanking.CELL, FECHA);

        verify(repository, times(2)).findByTipoAndFechaOrderByPosicion(any(), any());
    }

    @Test
    @DisplayName("fecha distinta no comparte entrada de cache")
    void fechaDistintaNoComparteCache() {
        adapter.porTipoYFecha(TipoRanking.LEAGUE, FECHA);
        adapter.porTipoYFecha(TipoRanking.LEAGUE, FECHA.plusDays(1));

        verify(repository, times(2)).findByTipoAndFechaOrderByPosicion(any(), any());
    }

    @Test
    @DisplayName("reemplazar() invalida la entrada de ese tipo+fecha: la siguiente lectura vuelve a pegar la base")
    void reemplazarInvalidaLaEntradaCacheada() {
        adapter.porTipoYFecha(TipoRanking.LEAGUE, FECHA);

        adapterEscritura.reemplazar(TipoRanking.LEAGUE, FECHA, List.of());

        adapter.porTipoYFecha(TipoRanking.LEAGUE, FECHA);

        verify(repository, times(2)).findByTipoAndFechaOrderByPosicion(any(), any());
    }

    @Configuration
    @EnableCaching
    static class Contexto {

        @Bean
        CaffeineCacheManager cacheManager() {
            CaffeineCacheManager manager = new CaffeineCacheManager();
            manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(20)));
            return manager;
        }

        @Bean
        SpringDataRankingAprendizRepository springDataRankingAprendizRepository() {
            return mock(SpringDataRankingAprendizRepository.class);
        }

        @Bean
        UserSummaryFinder userSummaryFinder() {
            return mock(UserSummaryFinder.class);
        }

        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean
        RankingPersistenceMapper rankingPersistenceMapper() {
            return new RankingPersistenceMapper();
        }

        @Bean
        RankingPersistenceAdapter rankingPersistenceAdapter(SpringDataRankingAprendizRepository repository,
                RankingPersistenceMapper mapper, JdbcTemplate jdbcTemplate, UserSummaryFinder userSummaryFinder) {
            return new RankingPersistenceAdapter(repository, mapper, jdbcTemplate, userSummaryFinder);
        }
    }
}
