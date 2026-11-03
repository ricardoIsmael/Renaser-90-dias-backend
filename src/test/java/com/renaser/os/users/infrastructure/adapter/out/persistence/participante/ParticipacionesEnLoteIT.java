package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auditoria de consultas del 2026-11-02 (H-01): {@code deParticipantes} contesta por VARIAS personas en UNA consulta y con lo mismo que
 * {@code deParticipante} de cada una: la activada con su formacion, la que existe sin programa y la que no existe (que no aparece).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ParticipacionesEnLoteIT {

    @Autowired
    private ParticipacionProgramaFinder participaciones;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> usuarios = new ArrayList<>();

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
    }

    private UUID usuario(String rol) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test", "Lote " + id, rol);
        usuarios.add(id);
        return id;
    }

    private void programa(UUID id, String formacion, boolean activado) {
        jdbc.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, timezone, formacion)
                VALUES (?, 5, ?, ?, 'America/Lima', ?)
                """, id, LocalDate.now().minusDays(4), activado ? Timestamp.from(Instant.now()) : null, formacion);
    }

    @Test
    @DisplayName("varias personas en una lectura: la inscrita con su formacion, la que existe sin programa, y la que no existe no aparece; cada una igual que por separado")
    void variasEnUnaLectura() {
        UUID escuela = usuario("APRENDIZ");
        programa(escuela, "ESCUELA", true);
        UUID diplomado = usuario("APRENDIZ");
        programa(diplomado, "DIPLOMADO", false);
        UUID sinPrograma = usuario("MENTOR");
        UUID inexistente = UUID.randomUUID();

        Map<UserId, ParticipacionPrograma> lote = participaciones.deParticipantes(List.of(UserId.of(escuela), UserId.of(diplomado),
                UserId.of(sinPrograma), UserId.of(inexistente)));

        assertThat(lote).containsOnlyKeys(UserId.of(escuela), UserId.of(diplomado), UserId.of(sinPrograma));
        assertThat(lote.get(UserId.of(escuela)).inscrito()).isTrue();
        assertThat(lote.get(UserId.of(escuela)).formacion()).isEqualTo(Formacion.ESCUELA);
        assertThat(lote.get(UserId.of(escuela)).activado()).isTrue();
        assertThat(lote.get(UserId.of(diplomado)).formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(lote.get(UserId.of(diplomado)).activado()).isFalse();
        assertThat(lote.get(UserId.of(sinPrograma)).inscrito()).isFalse();
        for (UserId id : lote.keySet()) {
            assertThat(lote.get(id)).as("%s, igual que deParticipante", id).isEqualTo(participaciones.deParticipante(id).orElseThrow());
        }
        assertThat(participaciones.deParticipantes(List.of())).isEmpty();
    }
}
