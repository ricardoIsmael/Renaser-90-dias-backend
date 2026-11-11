package com.renaser.os.habits.application.services;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.politica.PoliticaPostDiarioComunidad;
import com.renaser.os.habits.application.ports.in.registro.CerrarPostDiarioComunidadUseCase;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 048, fase 2 (D17, FR-020), sobre Postgres real: una publicación escrita sin red el día 6 por la noche y llegada el 7
 * cierra el «Post diario» del 6 —ya vencido por el barrido— con la hora real, aunque la publicación quede guardada con la
 * hora de llegada (aparece arriba, D18). La política del hábito («¿publicó en el Muro ese día?») la encuentra por la hora
 * real de {@code publicaciones_sin_red} (V189). Una reentrega no vuelve a pagar.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PublicarEnElMuroSinRedIT {

    private static final LocalDate EL_6 = LocalDate.of(2026, 10, 6);
    /** 21:30 del 6 en Lima. */
    private static final Instant ESCRITA = Instant.parse("2026-10-07T02:30:00Z");
    /** 10:00 del 7 en Lima. */
    private static final Instant LLEGA = Instant.parse("2026-10-07T15:00:00Z");

    @Autowired private CerrarPostDiarioComunidadUseCase cerrarPostDiario;
    @Autowired private SaveRegistroHabitoPort saveRegistroPort;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transacciones;
    @Autowired private RelojDePrueba reloj;

    private UserId persona;
    private RegistroHabitoId registroDel6;
    private UUID publicacion;

    @BeforeEach
    void sembrar() {
        persona = UserId.of(UUID.randomUUID());
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) VALUES (?, ?, 'Fixture', 'APRENDIZ', 'ACTIVO')",
                persona.value(), persona + "@renaser.test");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone) VALUES (?, 5, 'America/Lima')",
                persona.value());
        HabitoId postDiario = HabitoId.of(jdbc.queryForObject(
                "SELECT id FROM renaser.habitos WHERE clave_sistema = ? AND formacion = ?", UUID.class,
                PoliticaPostDiarioComunidad.CLAVE_SISTEMA, com.renaser.os.shared.domain.Formacion.ESCUELA.codigo()));
        registroDel6 = RegistroHabitoId.of(UUID.randomUUID());
        RegistroHabito registro = RegistroHabito.generar(registroDel6, persona, postDiario, EL_6, 5, TipoDia.DISCIPLINA, false,
                Instant.parse("2026-10-06T05:00:00Z"));
        registro.expirar(Instant.parse("2026-10-07T05:10:00Z")); // el barrido ya lo venció cuando llega
        saveRegistroPort.save(registro);

        publicacion = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.publicaciones_muro (id, autor_id, tipo, texto, creado_en) VALUES (?, ?, 'MANUAL', ?, ?)",
                publicacion, persona.value(), "Escrita sin señal en la noche", Timestamp.from(LLEGA));
        jdbc.update("INSERT INTO renaser.publicaciones_sin_red (autor_id, id_cliente, publicacion_id, hecho_en, origen_de_la_hora) "
                + "VALUES (?, ?, ?, ?, 'RELOJ_DEL_SISTEMA')", persona.value(), UUID.randomUUID(), publicacion, Timestamp.from(ESCRITA));
        reloj.fijar(LLEGA);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", persona.value());
    }

    @Test
    @DisplayName("Escrita sin red el 6 y llegada el 7: el «Post diario» del 6 queda cumplido con la hora real, una sola vez")
    void cuentaElDiaEnQueSeEscribio() { // spec 048 FR-020 SC-005
        transacciones.executeWithoutResult(s -> cerrarPostDiario.alPublicarSinRed(persona, publicacion, ESCRITA));
        transacciones.executeWithoutResult(s -> cerrarPostDiario.alPublicarSinRed(persona, publicacion, ESCRITA));

        var fila = jdbc.queryForMap("SELECT estado, puntos_otorgados, completado_en FROM renaser.registros_habito WHERE id = ?",
                registroDel6.value());
        assertThat(fila.get("estado")).isEqualTo("COMPLETADO");
        assertThat(((Timestamp) fila.get("completado_en")).toInstant()).isEqualTo(ESCRITA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.envios_sin_red WHERE participante_id = ?", Integer.class,
                persona.value())).as("la reentrega no vuelve a pagar").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT creado_en FROM renaser.publicaciones_muro WHERE id = ?", Timestamp.class, publicacion)
                .toInstant()).as("la publicación sigue con la hora de llegada (D18)").isEqualTo(LLEGA);
    }

    @Test
    @DisplayName("Borrada la publicación antes de cerrar el hábito, su hora real se va con ella y no cuenta")
    void borradaNoCuenta() { // spec 048 FR-018
        jdbc.update("DELETE FROM renaser.publicaciones_muro WHERE id = ?", publicacion);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.publicaciones_sin_red WHERE publicacion_id = ?", Integer.class,
                publicacion)).isZero();

        try {
            transacciones.executeWithoutResult(s -> cerrarPostDiario.alPublicarSinRed(persona, publicacion, ESCRITA));
        } catch (RuntimeException rechazadoPorLaPolitica) {
            // la política dice que ese día no publicó: el oyente lo registra y sigue
        }

        assertThat(jdbc.queryForObject("SELECT estado FROM renaser.registros_habito WHERE id = ?", String.class, registroDel6.value()))
                .isEqualTo("EXPIRADO");
    }
}
