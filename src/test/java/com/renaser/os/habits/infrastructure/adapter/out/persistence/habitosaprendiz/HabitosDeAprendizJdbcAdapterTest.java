package com.renaser.os.habits.infrastructure.adapter.out.persistence.habitosaprendiz;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.out.habitosaprendiz.LeerHabitosPersonalizadosPort.FilaHabitoDeAprendiz;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integracion real contra Postgres (§0.2: todo adaptador nuevo se prueba con Testcontainers).
 * Lo que se verifica de verdad aca es lo que un test unitario no puede: que la unica consulta
 * cruce las seis tablas SIN multiplicar filas, que respete la baja logica y el ambito, y que
 * elija el horario de catalogo vigente para el dia de programa.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class HabitosDeAprendizJdbcAdapterTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 8, 24);
    private static final int DIA_PROGRAMA = 30;

    @Autowired
    private HabitosDeAprendizJdbcAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId aprendiz;
    private UserId otroAprendiz;
    private HabitoId agua;
    private HabitoId correr;
    private HabitoId soloDomingo;
    private HabitoId retirado;
    private HabitoId personalAjeno;

    @BeforeEach
    void sembrarFixtures() {
        aprendiz = nuevoParticipante();
        otroAprendiz = nuevoParticipante();

        agua = nuevoHabitoDeSistema("Agua al despertar", 1, true, false);
        // dos tramos de catalogo: el vigente para el dia 30 es el segundo, no el primero
        nuevoHorario(agua, 1, 10, "DISCIPLINA", "05:00", "06:00");
        nuevoHorario(agua, 11, null, "DISCIPLINA", "06:00", "07:00");
        nuevoRenombre(aprendiz, agua, "Mi vaso de agua");
        nuevaPreferencia(aprendiz, agua, "07:30", null, true, 15);
        nuevoCambioPendiente(aprendiz, agua, "08:00", "09:00", LocalDate.of(2026, 8, 26));
        nuevoDesbloqueo(aprendiz, agua, 5, false);

        correr = nuevoHabitoPersonal(aprendiz, "Correr 5k", 2, true);
        nuevoHorario(correr, 1, null, "TODOS", "18:00", "19:00");
        nuevoDiaSemanal(aprendiz, correr, LocalDate.of(2026, 8, 27), LUNES);
        nuevoDesbloqueo(aprendiz, correr, 3, true);

        soloDomingo = nuevoHabitoDeSistema("Revision semanal", 3, true, false);
        nuevoHorario(soloDomingo, 1, null, "DOMINGO", "10:00", "12:00");

        retirado = nuevoHabitoDeSistema("Habito dado de baja", 4, false, false);
        nuevoHorario(retirado, 1, null, "DISCIPLINA", "07:00", "08:00");

        personalAjeno = nuevoHabitoPersonal(otroAprendiz, "Habito de otra persona", 5, false);
        nuevoHorario(personalAjeno, 1, null, "DISCIPLINA", "07:00", "08:00");
    }

    private List<FilaHabitoDeAprendiz> leerDiaHabil() {
        return adapter.deAprendiz(aprendiz, DIA_PROGRAMA, TipoDia.DISCIPLINA, LUNES);
    }

    /**
     * Las migraciones V4/V9 siembran el catalogo real de produccion, asi que el resultado
     * SIEMPRE trae mas filas que las de esta siembra: se busca por id en vez de por indice.
     */
    private static FilaHabitoDeAprendiz porId(List<FilaHabitoDeAprendiz> filas, HabitoId habitoId) {
        return filas.stream().filter(f -> f.habitoId().equals(habitoId)).findFirst().orElseThrow();
    }

    @Test
    void traeElCatalogoActivoMasLosPersonalesDelPropioAprendizYNadaMas() {
        List<FilaHabitoDeAprendiz> filas = leerDiaHabil();

        assertThat(filas).extracting(FilaHabitoDeAprendiz::habitoId)
                .contains(agua, correr, soloDomingo)
                .doesNotContain(retirado, personalAjeno);
    }

    @Test
    void noMultiplicaFilasAunqueCruceSeisTablas() {
        List<FilaHabitoDeAprendiz> filas = leerDiaHabil();

        assertThat(filas).extracting(FilaHabitoDeAprendiz::habitoId).doesNotHaveDuplicates();
    }

    @Test
    void resuelveElHorarioDeCatalogoVigenteParaElDiaDePrograma() {
        FilaHabitoDeAprendiz fila = porId(leerDiaHabil(), agua);

        assertThat(fila.horaDisparoCatalogo()).isEqualTo(LocalTime.of(6, 0));
        assertThat(fila.horaLimiteCatalogo()).isEqualTo(LocalTime.of(7, 0));
    }

    @Test
    void traeRenombrePreferenciaCambioPendienteYDesbloqueoDelAprendiz() {
        FilaHabitoDeAprendiz fila = porId(leerDiaHabil(), agua);

        assertThat(fila.tituloCatalogo()).isEqualTo("Agua al despertar");
        assertThat(fila.tituloPersonal()).isEqualTo("Mi vaso de agua");
        assertThat(fila.esPersonal()).isFalse();
        assertThat(fila.tipo()).isEqualTo(TipoHabito.CHECKBOX);
        assertThat(fila.categoriaClave()).isEqualTo("CUERPO");
        assertThat(fila.horaDisparoPreferencia()).isEqualTo(LocalTime.of(7, 30));
        assertThat(fila.horaLimitePreferencia()).isNull();
        assertThat(fila.recordatorioActivo()).isTrue();
        assertThat(fila.minutosRecordatorio()).isEqualTo(15);
        assertThat(fila.horaDisparoPendiente()).isEqualTo(LocalTime.of(8, 0));
        assertThat(fila.fechaEfectivaPendiente()).isEqualTo(LocalDate.of(2026, 8, 26));
        assertThat(fila.diaDesbloqueo()).isEqualTo(5);
        assertThat(fila.desbloqueoElegidoPorLaPersona()).isFalse();
    }

    @Test
    void elHabitoPersonalTraeSuDiaSemanalElegidoYSuDesbloqueoManual() {
        FilaHabitoDeAprendiz fila = porId(leerDiaHabil(), correr);

        assertThat(fila.esPersonal()).isTrue();
        assertThat(fila.eleccionDiaSemanal()).isTrue();
        assertThat(fila.diaSemanalElegido()).isEqualTo(LocalDate.of(2026, 8, 27));
        assertThat(fila.desbloqueoElegidoPorLaPersona()).isTrue();
        assertThat(fila.tituloPersonal()).isNull();
    }

    @Test
    void unHorarioDeDomingoNoRigeUnDiaDeDisciplina() {
        FilaHabitoDeAprendiz enDiaHabil = porId(leerDiaHabil(), soloDomingo);
        FilaHabitoDeAprendiz enDomingo = porId(
                adapter.deAprendiz(aprendiz, DIA_PROGRAMA, TipoDia.DOMINGO, LUNES), soloDomingo);

        assertThat(enDiaHabil.horaDisparoCatalogo()).isNull();
        assertThat(enDomingo.horaDisparoCatalogo()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void laEleccionDeOtraSemanaNoSeCuela() {
        List<FilaHabitoDeAprendiz> filas = adapter.deAprendiz(aprendiz, DIA_PROGRAMA, TipoDia.DISCIPLINA,
                LUNES.plusWeeks(1));

        assertThat(porId(filas, correr).diaSemanalElegido()).isNull();
    }

    // ── siembra ────────────────────────────────────────────────────────────────

    private UserId nuevoParticipante() {
        UserId id = UserId.of(UUID.randomUUID());
        ejecutar("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (:id, :email, 'Fixture', 'APRENDIZ', 'ACTIVO')
                """, "id", id.value(), "email", id + "@renaser.test");
        ejecutar("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa)
                VALUES (:id, 30)
                """, "id", id.value());
        return id;
    }

    private HabitoId nuevoHabitoDeSistema(String titulo, int orden, boolean activo, boolean eleccionSemanal) {
        HabitoId id = HabitoId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.habitos (id, ambito, titulo, tipo, categoria_clave, orden, activo,
                                                     eleccion_dia_semanal)
                        VALUES (:id, 'SISTEMA', :titulo, 'CHECKBOX', 'CUERPO', :orden, :activo, :semanal)
                        """)
                .setParameter("id", id.value()).setParameter("titulo", titulo).setParameter("orden", orden)
                .setParameter("activo", activo).setParameter("semanal", eleccionSemanal).executeUpdate();
        return id;
    }

    private HabitoId nuevoHabitoPersonal(UserId duenio, String titulo, int orden, boolean eleccionSemanal) {
        HabitoId id = HabitoId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.habitos (id, ambito, participante_id, titulo, tipo, categoria_clave,
                                                     orden, activo, eleccion_dia_semanal)
                        VALUES (:id, 'PERSONAL', :duenio, :titulo, 'CHECKBOX', 'CUERPO', :orden, true, :semanal)
                        """)
                .setParameter("id", id.value()).setParameter("duenio", duenio.value()).setParameter("titulo", titulo)
                .setParameter("orden", orden).setParameter("semanal", eleccionSemanal).executeUpdate();
        return id;
    }

    private void nuevoHorario(HabitoId habitoId, int diaInicio, Integer diaFin, String tipoDia, String disparo,
                               String limite) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.horarios_habito (habito_id, dia_inicio, dia_fin, tipo_dia, hora_disparo,
                                                             hora_limite)
                        VALUES (:habito, :inicio, CAST(:fin AS smallint), CAST(:tipoDia AS renaser.tipo_dia),
                                CAST(:disparo AS time), CAST(:limite AS time))
                        """)
                .setParameter("habito", habitoId.value()).setParameter("inicio", diaInicio)
                .setParameter("fin", diaFin).setParameter("tipoDia", tipoDia).setParameter("disparo", disparo)
                .setParameter("limite", limite).executeUpdate();
    }

    private void nuevoRenombre(UserId participanteId, HabitoId habitoId, String tituloPersonal) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.renombres_habito (participante_id, habito_id, titulo_personal, motivo)
                        VALUES (:participante, :habito, :titulo, 'test')
                        """)
                .setParameter("participante", participanteId.value()).setParameter("habito", habitoId.value())
                .setParameter("titulo", tituloPersonal).executeUpdate();
    }

    private void nuevaPreferencia(UserId participanteId, HabitoId habitoId, String disparo, String limite,
                                   boolean recordatorio, Integer minutos) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.preferencias_horario (participante_id, habito_id, hora_disparo,
                                                                  hora_limite, recordatorio_activo,
                                                                  minutos_recordatorio)
                        VALUES (:participante, :habito, CAST(:disparo AS time), CAST(:limite AS time), :recordatorio,
                                CAST(:minutos AS smallint))
                        """)
                .setParameter("participante", participanteId.value()).setParameter("habito", habitoId.value())
                .setParameter("disparo", disparo).setParameter("limite", limite)
                .setParameter("recordatorio", recordatorio).setParameter("minutos", minutos).executeUpdate();
    }

    private void nuevoCambioPendiente(UserId participanteId, HabitoId habitoId, String disparo, String limite,
                                       LocalDate fechaEfectiva) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.cambios_horario_pendientes (participante_id, habito_id, hora_disparo,
                                                                        hora_limite, fecha_efectiva)
                        VALUES (:participante, :habito, CAST(:disparo AS time), CAST(:limite AS time), :fecha)
                        """)
                .setParameter("participante", participanteId.value()).setParameter("habito", habitoId.value())
                .setParameter("disparo", disparo).setParameter("limite", limite)
                .setParameter("fecha", fechaEfectiva).executeUpdate();
    }

    private void nuevoDesbloqueo(UserId participanteId, HabitoId habitoId, int dia, boolean elegidoPorLaPersona) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.desbloqueos_habito (participante_id, habito_id, dia_desbloqueo,
                                                                elegido_en)
                        VALUES (:participante, :habito, :dia, CAST(:elegidoEn AS timestamptz))
                        """)
                .setParameter("participante", participanteId.value()).setParameter("habito", habitoId.value())
                .setParameter("dia", dia)
                .setParameter("elegidoEn", elegidoPorLaPersona ? "2026-08-20T10:00:00Z" : null).executeUpdate();
    }

    private void nuevoDiaSemanal(UserId participanteId, HabitoId habitoId, LocalDate fechaEjecucion,
                                  LocalDate semanaInicio) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.dias_semanales_habito (participante_id, habito_id, fecha_ejecucion,
                                                                   semana_inicio)
                        VALUES (:participante, :habito, :fecha, :semana)
                        """)
                .setParameter("participante", participanteId.value()).setParameter("habito", habitoId.value())
                .setParameter("fecha", fechaEjecucion).setParameter("semana", semanaInicio).executeUpdate();
    }

    private void ejecutar(String sql, Object... paresNombreValor) {
        var query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < paresNombreValor.length; i += 2) {
            query.setParameter((String) paresNombreValor[i], paresNombreValor[i + 1]);
        }
        query.executeUpdate();
    }
}
