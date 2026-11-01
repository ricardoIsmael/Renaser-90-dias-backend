package com.renaser.os.community.application.services;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.AsignarAprendizCelulaUseCase.AsignarAprendizCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.SumarAprendizAGrupoUseCase;
import com.renaser.os.community.application.ports.in.celula.SumarAprendizAGrupoUseCase.SumarAprendizAGrupoCommand;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionInvalidaException;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.formacion.TrasladarAprendizDeFormacionUseCase;
import com.renaser.os.users.application.ports.in.formacion.TrasladarAprendizDeFormacionUseCase.TrasladarCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los grupos y las formaciones (spec 022) contra Postgres real: un grupo es de UNA formación y solo recibe a gente de esa formación (FR-010), y cuando se
 * traslada a un aprendiz a otra formación el grupo que dejaba se cierra, entra a la recepción de la nueva y su puntaje arranca de cero. Los efectos que
 * cruzan módulos (grupo y puntos) llegan por eventos después del commit, así que se esperan con una espera acotada.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class GruposPorFormacionIT {

    private static final Instant AHORA = Instant.parse("2026-10-31T15:00:00Z");

    @Autowired
    private SumarAprendizAGrupoUseCase sumar;
    @Autowired
    private AsignarAprendizCelulaUseCase asignar;
    @Autowired
    private TrasladarAprendizDeFormacionUseCase trasladar;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID cohorte;
    private UserId admin;
    private final List<UUID> grupos = new ArrayList<>();
    private final List<UUID> usuarios = new ArrayList<>();

    @BeforeEach
    void sembrar() {
        reloj.fijar(AHORA);
        grupos.clear();
        usuarios.clear();
        cohorte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte de formaciones', DATE '2026-10-01')", cohorte);
        admin = usuario("ADMIN", "Admin Uno");
    }

    @AfterEach
    void limpiar() {
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        grupos.forEach(id -> jdbc.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        jdbc.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorte);
        jdbc.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    @Test
    @DisplayName("Un grupo de una formación no recibe a un aprendiz de otra, ni al asignarlo ni al sumarlo")
    void unGrupoNoRecibeAGenteDeOtraFormacion() {
        UUID delDiplomado = grupo("Fénix del Diplomado", "DIPLOMADO", "REGULAR");
        UserId deLaEscuela = aprendiz("ESCUELA", 90);

        assertThatThrownBy(() -> sumar.sumar(new SumarAprendizAGrupoCommand(admin, CelulaId.of(delDiplomado), deLaEscuela)))
                .isInstanceOf(AsignacionInvalidaException.class).hasMessageContaining("Fénix del Diplomado");
        assertThatThrownBy(() -> asignar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(delDiplomado), deLaEscuela)))
                .isInstanceOf(AsignacionInvalidaException.class);
        assertThat(vigentes(deLaEscuela)).isZero();
    }

    @Test
    @DisplayName("Un aprendiz entra al grupo de su propia formación")
    void elGrupoDeSuFormacionLoAcepta() {
        UUID delDiplomado = grupo("Fénix del Diplomado", "DIPLOMADO", "REGULAR");
        UserId deDiplomado = aprendiz("DIPLOMADO", 365);

        asignar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(delDiplomado), deDiplomado));

        assertThat(vigentes(deDiplomado)).isEqualTo(1);
    }

    @Test
    @DisplayName("Al trasladarlo: sale del grupo de la formación vieja, entra a la recepción de la nueva y su puntaje arranca de cero")
    void trasladarReubicaYReiniciaElPuntaje() {
        UUID viejo = grupo("Fénix de la Escuela", "ESCUELA", "REGULAR");
        UUID recepcionNueva = grupo("Bienvenida del Diplomado", "DIPLOMADO", "RECEPCION");
        UserId ana = aprendiz("ESCUELA", 90);
        // El Día 1 fue hace 90 días: hoy es su día 90 y terminó, así que se la puede pasar de formación sin motivo.
        jdbc.update("UPDATE renaser.participantes_programa SET programa_completado = true, fecha_inicio = DATE '2026-08-03' WHERE usuario_id = ?", ana.value());
        jdbc.update("INSERT INTO renaser.puntajes_participante (participante_id, coherencia, puntos_liga, racha_actual, racha_maxima) "
                + "VALUES (?, 80, 250, 5, 9)", ana.value());
        asignar.asignar(new AsignarAprendizCelulaCommand(admin, CelulaId.of(viejo), ana));
        assertThat(vigentes(ana)).isEqualTo(1);
        // Pasa el tiempo entre que lo ubicaron y lo trasladan: un periodo de grupo no puede cerrarse en el mismo instante en que abrió.
        reloj.avanzar(java.time.Duration.ofHours(1));

        trasladar.trasladar(new TrasladarCommand(admin, ana, Formacion.DIPLOMADO, 90, false, null));

        esperar("sale del grupo viejo", () -> vigentesEn(ana, viejo) == 0);
        esperar("entra a la recepción del Diplomado", () -> vigentesEn(ana, recepcionNueva) == 1);
        esperar("el puntaje arranca de cero", () -> jdbc.queryForObject(
                "SELECT puntos_liga FROM renaser.puntajes_participante WHERE participante_id = ?", Integer.class, ana.value()) == 100);
        assertThat(jdbc.queryForObject("SELECT coherencia || ':' || racha_actual FROM renaser.puntajes_participante WHERE participante_id = ?",
                String.class, ana.value())).isEqualTo("100.00:0");
    }

    // ---------------------------------------------------------------------------------------------------------- apoyo

    private static void esperar(String que, BooleanSupplier condicion) {
        long limite = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < limite) {
            if (condicion.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("No llegó a cumplirse: " + que);
    }

    private UUID grupo(String nombre, String formacion, String tipo) {
        UUID id = UUID.randomUUID();
        boolean regular = "REGULAR".equals(tipo);
        jdbc.update("INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin, formacion) "
                + "VALUES (?, ?, ?, CAST(? AS renaser.tipo_celula), " + (regular ? "DATE '2026-10-01', DATE '2026-12-31'" : "NULL, NULL") + ", ?)",
                id, nombre, cohorte, tipo, formacion);
        grupos.add(id);
        return id;
    }

    private UserId usuario(String rol, String nombre) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), 'ACTIVO')",
                id, id + "@formaciones.test", nombre, rol);
        usuarios.add(id);
        return UserId.of(id);
    }

    private UserId aprendiz(String formacion, int duracion) {
        UserId id = usuario("APRENDIZ", "Aprendiz de " + formacion);
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa, programa_activado_en, timezone, formacion, "
                + "duracion_dias) VALUES (?, DATE '2026-10-26', 5, TIMESTAMPTZ '2026-10-25 12:00:00+00', 'America/Lima', ?, ?)",
                id.value(), formacion, duracion);
        return id;
    }

    private int vigentes(UserId usuario) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM renaser.asignaciones_celula WHERE usuario_id = ? AND fin IS NULL", Integer.class, usuario.value());
        return n == null ? 0 : n;
    }

    private int vigentesEn(UserId usuario, UUID grupo) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM renaser.asignaciones_celula WHERE usuario_id = ? AND celula_id = ? AND fin IS NULL",
                Integer.class, usuario.value(), grupo);
        return n == null ? 0 : n;
    }
}
