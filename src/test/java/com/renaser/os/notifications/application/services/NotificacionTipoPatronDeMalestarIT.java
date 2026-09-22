package com.renaser.os.notifications.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase;
import com.renaser.os.notifications.application.ports.in.notificacion.EmitirNotificacionUseCase.EmitirNotificacionCommand;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La unica parte de agregar un valor a {@code TipoNotificacion} que NO rompe la compilacion:
 * la migracion (E-183).
 *
 * <p>Sin el {@code ALTER TYPE} de V59, todo esto compila y los tests unitarios pasan; el fallo
 * aparece recien contra Postgres, al INSERTAR, con
 * {@code invalid input value for enum renaser.tipo_notificacion}. Y aparece en produccion, no en
 * CI — porque el unico camino que emite este tipo es un aviso que se dispara cuando alguien
 * repitio expresiones de malestar, o sea el peor momento para descubrir que la fila no entra.
 *
 * <p>Este test lo mueve a CI: emite el tipo nuevo contra el Postgres real de Testcontainers y
 * confirma que la fila queda escrita con ese valor.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NotificacionTipoPatronDeMalestarIT {

    @Autowired
    private EmitirNotificacionUseCase emitirNotificacionUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V59: el valor PATRON_DE_MALESTAR_REPETIDO existe en el enum de Postgres y la fila entra")
    void elTipoNuevoSeEscribeContraPostgresReal() {
        UUID administradorId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol)
                VALUES (?, ?, 'Admin de Prueba', 'ADMIN')
                """, administradorId, "admin-" + administradorId + "@renaser.com");
        UUID claveDelEpisodio = UUID.randomUUID();

        emitirNotificacionUseCase.emitir(new EmitirNotificacionCommand(UserId.of(administradorId),
                TipoNotificacion.PATRON_DE_MALESTAR_REPETIDO, "Un patron que conviene mirar",
                "Ana Quispe escribio al menos 3 veces en los ultimos 7 dias expresiones de malestar al asistente.",
                "/admin/trainees/" + UUID.randomUUID(), claveDelEpisodio));

        String tipoGuardado = jdbcTemplate.queryForObject(
                "select tipo::text from renaser.notificaciones where usuario_id = ? and origen_evento_id = ?",
                String.class, administradorId, claveDelEpisodio);
        assertThat(tipoGuardado).isEqualTo("PATRON_DE_MALESTAR_REPETIDO");
    }
}
