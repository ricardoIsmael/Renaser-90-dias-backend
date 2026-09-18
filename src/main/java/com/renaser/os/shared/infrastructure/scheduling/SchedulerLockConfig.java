package com.renaser.os.shared.infrastructure.scheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Lock distribuido para los pocos {@code @Scheduled} que de verdad lo necesitan con N
 * instancias (C-5, docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html — ver el
 * analisis caso por caso en docs/informes/auditoria-fixes/C-5.md). NO se aplica a los once
 * schedulers del repo por las dudas: la mayoria ya es segura por diseno (idempotencia de
 * dominio, {@code INSERT ... ON CONFLICT}, o {@code FOR UPDATE SKIP LOCKED} de vida corta) y
 * agregarle un lock encima solo suma una fila de contencion y un modo de falla nuevo sin
 * necesidad real.
 *
 * <p>Vive en {@code shared/} (modulo OPEN, igual que {@code CacheConfig}) porque es
 * infraestructura transversal: el {@link LockProvider} es uno solo para todo el proceso, y los
 * modulos que anotan un metodo con {@code @SchedulerLock} no tienen por que declarar su propio
 * proveedor.
 *
 * <blockquote><b>Corregido 2026-09-18.</b> Aca decia que el lock <i>"NO se aplica a los once
 * schedulers del repo"</i> y que eran <i>"los tres modulos que hoy anotan un metodo"</i>
 * ({@code evidence}, {@code habits}, {@code rag}). Al revisarlos uno por uno son <b>20</b>
 * {@code @Scheduled} en nueve modulos, y <b>18</b> llevan lock. Los dos que no, no son un olvido
 * y quedan anotados para que nadie "los arregle": {@code PresenciaDeSockets} DEBE correr en todas
 * las instancias —cada una refresca sus propios sockets, y bloquearla apagaria la presencia de la
 * gente conectada a la instancia que pierda— y {@code PurgaNotificacionesScheduler} es un DELETE
 * por marca de tiempo, idempotente: dos corridas borran el mismo conjunto.</blockquote>
 *
 * <p>{@code defaultLockAtMostFor} es una red de seguridad que casi no se usa: los schedulers
 * declaran su propio {@code lockAtMostFor} configurable (ver cada uno). Este default generoso
 * (30 min) solo protege si alguien agrega un {@code @SchedulerLock} nuevo sin pensar el valor.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
class SchedulerLockConfig {

    /**
     * {@code withTableName("renaser.shedlock")} explicito: la conexion JDBC de este proyecto
     * NO fija {@code search_path} a nivel de pool (no hay {@code currentSchema} en la URL ni
     * {@code hibernate.default_schema} en {@code application.yaml} — cada entidad/adaptador
     * califica el esquema a mano, ver {@code UserJpaEntity} y
     * {@code RecordatorioPersistenceAdapter}). Sin esto, {@link JdbcTemplateLockProvider}
     * buscaria una tabla {@code shedlock} en {@code public} y nunca encontraria la que crea
     * {@code V15__shedlock.sql} en {@code renaser}.
     */
    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
                        .withTableName("renaser.shedlock")
                        .usingDbTime() // hora del servidor de Postgres, no la del proceso Java (evita drift de reloj entre instancias)
                        .build());
    }
}
