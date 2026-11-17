package com.renaser.os.notifications.infrastructure.adapter.in.event;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.mentoring.api.AvisoDeAcompanamientoEvent;
import com.renaser.os.notifications.application.ports.out.push.PushPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #165 contra Postgres de verdad: los avisos de acompañamiento de días seguidos para el mismo
 * aprendiz y motivo no se acumulan. El episodio se mueve (la clave de deduplicación cambia) y antes cada
 * cambio dejaba otro aviso sin leer con la cifra vieja; ahora queda UNO sin leer, con la cifra de hoy.
 *
 * <p>Sin {@code @Transactional} de clase: el listener corre en su propia transacción y en otro hilo.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AvisoAcompanamientoSinAcumularIT {

    @MockitoBean
    private PushPort pushPort;
    @Autowired
    private AvisoAcompanamientoNotificationListener listener;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID mentor;
    private UUID grupo;
    private UUID ana;
    private UUID beto;

    @BeforeEach
    void semilla() {
        mentor = persona("Mentora");
        grupo = UUID.randomUUID();
        ana = UUID.randomUUID();
        beto = UUID.randomUUID();
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.notificaciones WHERE usuario_id = ?", mentor);
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", mentor);
    }

    @Test
    @DisplayName("dos días seguidos, mismo aprendiz y mismo motivo: queda un solo aviso sin leer, con la cifra del segundo día")
    void unoSoloConLaCifraDeHoy() throws InterruptedException {
        emitir(ana, "SIN_ACTIVIDAD", 44, "Ana Pérez");
        emitir(ana, "SIN_ACTIVIDAD", 45, "Ana Pérez");

        assertThat(sinLeer(ana)).containsExactly("Ana Pérez no registra actividad hace 45 días.");
        assertThat(total(ana)).as("no se borra nada: el anterior queda leído como historia").isEqualTo(2);
    }

    @Test
    @DisplayName("el otro motivo y otro aprendiz no se superan: son avisos distintos")
    void soloSuperaLoMismo() throws InterruptedException {
        emitir(ana, "SIN_ACTIVIDAD", 44, "Ana Pérez");
        emitir(ana, "EVIDENCIA_VENCIDA", 70, "Ana Pérez");
        emitir(beto, "SIN_ACTIVIDAD", 10, "Beto Ruiz");
        emitir(ana, "EVIDENCIA_VENCIDA", 63, "Ana Pérez");

        assertThat(sinLeer(ana)).containsExactlyInAnyOrder("Ana Pérez no registra actividad hace 44 días.",
                "Ana Pérez tiene 63 evidencias pendientes.");
        assertThat(sinLeer(beto)).containsExactly("Beto Ruiz no registra actividad hace 10 días.");
    }

    @Test
    @DisplayName("un aviso que el mentor ya leyó no se vuelve a tocar y el nuevo nace sin leer")
    void elLeidoSeQuedaLeido() throws InterruptedException {
        emitir(ana, "SIN_ACTIVIDAD", 44, "Ana Pérez");
        jdbc.update("UPDATE renaser.notificaciones SET leida_en = now() WHERE usuario_id = ?", mentor);
        emitir(ana, "SIN_ACTIVIDAD", 45, "Ana Pérez");

        assertThat(sinLeer(ana)).containsExactly("Ana Pérez no registra actividad hace 45 días.");
    }

    @Test
    @DisplayName("la migración V197 deja leídos los repetidos viejos, conserva el más reciente de cada motivo y no borra filas")
    void migracionDeLimpieza() throws Exception {
        String ruta = "/mentor/groups/" + grupo + "/learners/" + ana;
        sembrar(ruta, "Ana Pérez no registra actividad hace 44 días.", "3 days");
        sembrar(ruta, "Ana Pérez no registra actividad hace 45 días.", "2 days");
        sembrar(ruta, "Ana Pérez no registra actividad hace 17 días.", "1 day");
        sembrar(ruta, "Ana Pérez tiene 70 evidencias pendientes.", "2 days");
        sembrar(ruta, "Ana Pérez tiene 1 evidencia pendiente.", "1 day");

        String sql = new String(getClass().getResourceAsStream("/db/migration/V197__superar_avisos_de_acompanamiento_acumulados.sql")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        jdbc.execute(sql);
        jdbc.execute(sql); // idempotente

        assertThat(sinLeer(ana)).containsExactlyInAnyOrder("Ana Pérez no registra actividad hace 17 días.",
                "Ana Pérez tiene 1 evidencia pendiente.");
        assertThat(total(ana)).isEqualTo(5);
    }

    private void sembrar(String ruta, String cuerpo, String hace) {
        jdbc.update("""
                INSERT INTO renaser.notificaciones (usuario_id, tipo, titulo, cuerpo, ruta_app, creado_en)
                VALUES (?, CAST('ACOMPANAMIENTO_ALUMNO' AS renaser.tipo_notificacion), 'Novedades de acompañamiento', ?, ?,
                        now() - CAST(? AS interval))
                """, mentor, cuerpo, ruta, hace);
    }

    private void emitir(UUID alumno, String motivo, int magnitud, String nombre) throws InterruptedException {
        int antes = total(alumno) + total(otro(alumno));
        listener.on(new AvisoDeAcompanamientoEvent(UUID.randomUUID(), mentor, alumno, grupo, motivo, magnitud,
                nombre, Instant.now()));
        // El listener es asíncrono: se espera a que la fila nueva exista Y a que termine de superar a las viejas.
        for (int i = 0; i < 100 && total(alumno) + total(otro(alumno)) == antes; i++) {
            Thread.sleep(100);
        }
        Thread.sleep(400);
    }

    private UUID otro(UUID alumno) {
        return alumno.equals(ana) ? beto : ana;
    }

    private int total(UUID alumno) {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.notificaciones WHERE usuario_id = ? AND ruta_app LIKE ?",
                Integer.class, mentor, "%/learners/" + alumno);
    }

    private List<String> sinLeer(UUID alumno) {
        return jdbc.queryForList("""
                SELECT cuerpo FROM renaser.notificaciones
                WHERE usuario_id = ? AND ruta_app LIKE ? AND leida_en IS NULL ORDER BY id
                """, String.class, mentor, "%/learners/" + alumno);
    }

    private UUID persona(String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST('MENTOR' AS renaser.rol_usuario), CAST('ACTIVO' AS renaser.estado_usuario))
                """, id, id + "@renaser.test", nombre);
        return id;
    }
}
