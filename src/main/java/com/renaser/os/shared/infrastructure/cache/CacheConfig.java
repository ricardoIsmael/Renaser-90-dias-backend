package com.renaser.os.shared.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * CacheManager en memoria (Caffeine) para hot paths de lectura pura — CLAUDE.MD sec. 3 y 6
 * ("Caché local | Caffeine | Para el hot path de <1 ms real"), decision D-63.
 *
 * <p>Es infraestructura transversal, no logica de negocio: vive en {@code shared/} (modulo
 * OPEN) para que cualquier modulo pueda declarar {@code @Cacheable("nombre")} sobre un metodo
 * de SU PROPIO adaptador de persistencia y heredar esta misma politica de TTL/tamaño, sin
 * repetir la configuracion de Caffeine en cada modulo. La caché en si (que se cachea, con que
 * clave, cuando se invalida) es decision de cada adaptador — este archivo solo define el
 * "motor". Primer y unico consumidor hoy: {@code RankingPersistenceAdapter} (D-63).
 *
 * <p>{@code dynamic=true} (default de {@link CaffeineCacheManager} cuando no se le pasan
 * nombres fijos en el constructor): cualquier nombre de cache se crea al vuelo con esta misma
 * politica la primera vez que se usa. Evita que este archivo cambie cada vez que otro modulo
 * empiece a cachear algo — si el dia de mañana un cache necesita otra politica (mas tamaño, TTL
 * distinto), se declara su propio {@link CaffeineCacheManager} con nombre explicito.
 */
@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    CaffeineCacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                // 20s: dentro del rango 15-30s que un podio puede mostrar desactualizado sin
                // que nadie lo note (decision del dueño del proyecto, 2026-09-01). Se elige el
                // extremo bajo del rango, no el alto, porque el ranking no tiene todavia canal
                // de invalidacion entre instancias (CLAUDE.MD sec. 5.2.1: la caché de Caffeine
                // es por instancia; el TTL es la unica red de seguridad real hasta que exista
                // Redis Pub/Sub para esto). Con 20s, mil lecturas simultaneas del podio colapsan
                // en 1 sola consulta a Postgres igual que con 30s — el beneficio de saturacion
                // no cambia con el TTL exacto dentro de este rango, asi que se prioriza frescura.
                .expireAfterWrite(Duration.ofSeconds(20))
                // Techo generoso y barato: hoy son a lo sumo 4 tipos x pocas fechas por dia.
                .maximumSize(500));
        return manager;
    }
}
