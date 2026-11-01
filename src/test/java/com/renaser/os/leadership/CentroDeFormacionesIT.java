package com.renaser.os.leadership;

import com.renaser.os.FormacionesDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.leadership.application.ports.in.formacion.ConsultarCentroDeFormacionesUseCase;
import com.renaser.os.leadership.application.ports.in.formacion.ConsultarCentroDeFormacionesUseCase.DetalleDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.in.formacion.AsignarFormacionesDelPersonalUseCase;
import com.renaser.os.users.application.ports.in.formacion.AsignarFormacionesDelPersonalUseCase.AsignarFormacionesCommand;
import com.renaser.os.users.application.ports.in.formacion.ConfigurarFormacionUseCase;
import com.renaser.os.users.application.ports.in.formacion.ConfigurarFormacionUseCase.ConfigurarFormacionCommand;
import com.renaser.os.users.application.ports.in.formacion.TrasladarAprendizDeFormacionUseCase;
import com.renaser.os.users.application.ports.in.formacion.TrasladarAprendizDeFormacionUseCase.TrasladarCommand;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase.ListTraineesCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El centro «Formaciones» de Administración contra Postgres real (spec 022, historia 4): sus cifras salen de las mismas tablas que las
 * listas a las que llevan (FR-026), solo Administración y Alquimista activos lo ven, cambiar las duraciones no mueve a quien ya empezó
 * (FR-004) y una edición con una versión vieja se rechaza. La base se comparte entre las clases de esta corrida, así que se mira la
 * DIFERENCIA que deja lo sembrado y no el total.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CentroDeFormacionesIT {

    @Autowired
    private ConsultarCentroDeFormacionesUseCase centro;
    @Autowired
    private ListTraineesUseCase padron;
    @Autowired
    private ConfigurarFormacionUseCase configurar;
    @Autowired
    private AsignarFormacionesDelPersonalUseCase asignar;
    @Autowired
    private TrasladarAprendizDeFormacionUseCase trasladar;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> grupos = new ArrayList<>();
    private UUID cohorte;
    private UserId admin;

    @BeforeEach
    void sembrar() {
        usuarios.clear();
        grupos.clear();
        cohorte = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (?, 'Cohorte del centro', DATE '2026-10-01')", cohorte);
        admin = usuario("ADMIN", "ACTIVO");
    }

    @AfterEach
    void limpiar() {
        com.renaser.os.EventosPendientes.esperarQueTerminen(jdbc);
        usuarios.forEach(id -> jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        grupos.forEach(id -> jdbc.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        jdbc.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorte);
    }

    @Test
    @DisplayName("Las cifras del centro suman exactamente lo sembrado y coinciden con la lista de Personas de esa formación")
    void lasCifrasCoincidenConLasListas() {
        DetalleDeFormacion diplomadoAntes = centro.detalle(admin, Formacion.DIPLOMADO);
        DetalleDeFormacion escuelaAntes = centro.detalle(admin, Formacion.ESCUELA);
        long personasAntes = personasDe(Formacion.DIPLOMADO);

        aprendiz("DIPLOMADO", 365, false, false); // por empezar
        aprendiz("DIPLOMADO", 365, true, false);  // en curso
        aprendiz("DIPLOMADO", 90, true, true);    // terminó
        grupo("Grupo del Diplomado sin mentor", "DIPLOMADO", "REGULAR");
        grupo("Bienvenida del Diplomado", "DIPLOMADO", "RECEPCION");
        UserId mentor = usuario("MENTOR", "ACTIVO");
        asignar.asignar(new AsignarFormacionesCommand(admin, mentor, Set.of(Formacion.DIPLOMADO), Formacion.DIPLOMADO));

        DetalleDeFormacion diplomado = centro.detalle(admin, Formacion.DIPLOMADO);
        assertThat(diplomado.personas().aprendices() - diplomadoAntes.personas().aprendices()).isEqualTo(3);
        assertThat(diplomado.personas().porEmpezar() - diplomadoAntes.personas().porEmpezar()).isEqualTo(1);
        assertThat(diplomado.personas().enCurso() - diplomadoAntes.personas().enCurso()).isEqualTo(1);
        assertThat(diplomado.personas().terminaron() - diplomadoAntes.personas().terminaron()).isEqualTo(1);
        assertThat(diplomado.personas().personal() - diplomadoAntes.personas().personal()).isEqualTo(1);
        assertThat(diplomado.grupos().total() - diplomadoAntes.grupos().total()).isEqualTo(2);
        assertThat(diplomado.grupos().sinMentor() - diplomadoAntes.grupos().sinMentor()).isEqualTo(1);
        assertThat(diplomado.grupos().recepcion() - diplomadoAntes.grupos().recepcion()).isEqualTo(1);
        assertThat(diplomado.porAtender().aprendicesSinGrupo() - diplomadoAntes.porAtender().aprendicesSinGrupo()).isEqualTo(3);
        // FR-026: la cifra de aprendices es la de la lista a la que lleva «Ver personas».
        assertThat(personasDe(Formacion.DIPLOMADO) - personasAntes).isEqualTo(3);
        // Lo de la otra formación no se movió.
        DetalleDeFormacion escuela = centro.detalle(admin, Formacion.ESCUELA);
        assertThat(escuela.personas()).isEqualTo(escuelaAntes.personas());
        assertThat(escuela.grupos()).isEqualTo(escuelaAntes.grupos());
    }

    @Test
    @DisplayName("La tarjeta dice lo mismo que el detalle: aprendices, personal, grupos y lo por atender de esa formación")
    void laTarjetaDiceLoQueElDetalle() {
        aprendiz("CERTIFICACION", 90, true, false);
        grupo("Grupo de la Certificación sin mentor", "CERTIFICACION", "REGULAR");

        var tarjeta = centro.tarjetas(admin).stream().filter(t -> t.resumen().formacion() == Formacion.CERTIFICACION).findFirst().orElseThrow();
        DetalleDeFormacion detalle = centro.detalle(admin, Formacion.CERTIFICACION);
        assertThat(tarjeta.aprendicesActivos()).isEqualTo(detalle.personas().aprendices());
        assertThat(tarjeta.personal()).isEqualTo(detalle.personas().personal());
        assertThat(tarjeta.grupos()).isEqualTo(detalle.grupos().total());
        assertThat(tarjeta.porAtender()).isEqualTo(detalle.porAtender().gruposSinMentor() + detalle.porAtender().aprendicesSinGrupo());
        assertThat(centro.tarjetas(admin)).extracting(t -> t.resumen().formacion())
                .containsExactly(Formacion.ESCUELA, Formacion.DIPLOMADO, Formacion.CERTIFICACION);
    }

    @Test
    @DisplayName("Solo Administración y Alquimista activos ven el centro, asignan, trasladan y configuran")
    void soloAdministracionActiva() {
        UserId mentor = usuario("MENTOR", "ACTIVO");
        UserId aprendiz = aprendiz("ESCUELA", 90, true, true);
        UserId suspendida = usuario("ADMIN", "SUSPENDIDO");
        UserId alquimista = usuario("ALQUIMISTA", "ACTIVO");

        for (UserId noAutorizado : List.of(mentor, aprendiz, suspendida)) {
            assertThatThrownBy(() -> centro.tarjetas(noAutorizado)).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> centro.detalle(noAutorizado, Formacion.ESCUELA)).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> trasladar.trasladar(new TrasladarCommand(noAutorizado, aprendiz, Formacion.DIPLOMADO, 90, false, null)))
                    .isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> asignar.asignar(new AsignarFormacionesCommand(noAutorizado, mentor, Set.of(Formacion.ESCUELA), null)))
                    .isInstanceOf(NotAuthorizedException.class);
        }
        // El Alquimista tiene las mismas capacidades que Administración.
        assertThat(centro.tarjetas(alquimista)).hasSize(3);
    }

    @Test
    @DisplayName("Cambiar las duraciones no mueve a quien ya empezó, y una edición con versión vieja se rechaza")
    void cambiarLasDuracionesNoMueveAQuienYaEmpezo() {
        UserId beto = aprendiz("DIPLOMADO", 365, true, false);
        int version = centro.detalle(admin, Formacion.DIPLOMADO).resumen().version();
        var actual = centro.detalle(admin, Formacion.DIPLOMADO).resumen();

        configurar.configurar(new ConfigurarFormacionCommand(admin, Formacion.DIPLOMADO, actual.nombre(), actual.descripcion(), true, List.of(90), version));
        try {
            assertThat(jdbc.queryForObject("SELECT duracion_dias FROM renaser.participantes_programa WHERE usuario_id = ?", Integer.class, beto.value()))
                    .isEqualTo(365);
            assertThat(centro.detalle(admin, Formacion.DIPLOMADO).resumen().duracionesDias()).containsExactly(90);
            assertThat(centro.detalle(admin, Formacion.DIPLOMADO).resumen().version()).isEqualTo(version + 1);
            // La versión que se leyó antes ya no vale.
            assertThatThrownBy(() -> configurar.configurar(
                    new ConfigurarFormacionCommand(admin, Formacion.DIPLOMADO, actual.nombre(), actual.descripcion(), true, List.of(90, 365), version)))
                    .isInstanceOf(IllegalStateException.class);
            // Y ya no se puede pasar a nadie al Diplomado de un año mientras no lo ofrezca.
            UserId ana = aprendiz("ESCUELA", 90, true, true);
            assertThatThrownBy(() -> trasladar.trasladar(new TrasladarCommand(admin, ana, Formacion.DIPLOMADO, 365, false, null)))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            int vigente = centro.detalle(admin, Formacion.DIPLOMADO).resumen().version();
            configurar.configurar(new ConfigurarFormacionCommand(admin, Formacion.DIPLOMADO, actual.nombre(), actual.descripcion(), true,
                    actual.duracionesDias(), vigente));
        }
    }

    // ---------------------------------------------------------------------------------------------------------- apoyo

    private long personasDe(Formacion formacion) {
        return padron.listar(new ListTraineesCommand(admin, 0, 1, null, false, UserRole.TRAINEE, formacion)).total();
    }

    private UserId usuario(String rol, String estado) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) VALUES (?, ?, ?, CAST(? AS renaser.rol_usuario), "
                + "CAST(? AS renaser.estado_usuario))", id, id + "@centro.test", rol + " " + id.toString().substring(0, 6), rol, estado);
        FormacionesDePrueba.alPersonalLeAsignaLaEscuela(jdbc, id, rol);
        usuarios.add(id);
        return UserId.of(id);
    }

    private UserId aprendiz(String formacion, int duracion, boolean activado, boolean completado) {
        UserId id = usuario("APRENDIZ", "ACTIVO");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, timezone, "
                + "formacion, duracion_dias, programa_completado) VALUES (?, ?, DATE '2026-08-03', "
                + (activado ? "TIMESTAMPTZ '2026-08-02 12:00:00+00'" : "NULL") + ", 'America/Lima', ?, ?, ?)",
                id.value(), completado ? Math.min(duracion, 90) : 0, formacion, duracion, completado);
        return id;
    }

    private void grupo(String nombre, String formacion, String tipo) {
        UUID id = UUID.randomUUID();
        boolean regular = "REGULAR".equals(tipo);
        jdbc.update("INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo, periodo_inicio, periodo_fin, formacion) VALUES (?, ?, ?, "
                + "CAST(? AS renaser.tipo_celula), " + (regular ? "DATE '2026-10-01', DATE '2026-12-31'" : "NULL, NULL") + ", ?)",
                id, nombre, cohorte, tipo, formacion);
        grupos.add(id);
    }
}
