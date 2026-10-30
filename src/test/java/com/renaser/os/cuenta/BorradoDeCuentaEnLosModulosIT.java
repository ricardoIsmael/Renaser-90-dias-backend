package com.renaser.os.cuenta;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.calendar.application.ports.in.recordatorio.DespacharRecordatoriosUseCase;
import com.renaser.os.calendar.application.ports.in.recordatorio.GenerarRecordatoriosUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cada módulo borra lo suyo de una persona cuya cuenta se borra para siempre (D-243), contra Postgres de
 * verdad y con TODAS las implementaciones de {@link BorradoDeDatosDeCuenta} en el orden en que las inyecta
 * {@code users}.
 *
 * <p>La comprobación es del esquema entero, no de una lista a mano: se siembran dos personas con una fila
 * en cada tabla de los módulos, se borra una y se exige que (1) ninguna columna con id de persona del
 * esquema la siga nombrando fuera de las tablas de {@code users}, y (2) el conteo de filas de cada tabla
 * vuelva a ser el de antes de sembrarla, salvo en las tablas que sobreviven a propósito. Así, una tabla
 * nueva con datos de una persona que nadie borre hace fallar esta prueba.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BorradoDeCuentaEnLosModulosIT {

    /** Lo que borra {@code users} después de los módulos: acá la persona sigue. */
    private static final Set<String> TABLAS_DE_USERS = Set.of("usuarios", "participantes_programa",
            "perfiles_mentor", "identidades_externas", "solicitudes_cuenta", "ajustes_dia_programa",
            "auditoria_cambios_rol");

    /** Filas sembradas para la persona que sobreviven a propósito (son del grupo, del calendario, del
     * catálogo o de {@code users}); en las demás tablas no puede quedar ni una. */
    private static final Set<String> SOBREVIVEN = Set.of("usuarios", "participantes_programa", "perfiles_mentor",
            "cohortes", "celulas", "eventos", "cambios_bienvenida", "grupos",
            // V93 (D-256): la lista que la persona cerró es del evento, que sobrevive; pierde el autor.
            "listas_asistencia_evento");

    @Autowired
    private List<BorradoDeDatosDeCuenta> borrados;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private GenerarRecordatoriosUseCase generarRecordatorios;
    @Autowired
    private DespacharRecordatoriosUseCase despacharRecordatorios;

    private SemillaDeCuenta semilla;

    @BeforeEach
    void preparar() {
        semilla = new SemillaDeCuenta(jdbc);
    }

    @Test
    @DisplayName("los quince módulos se inyectan en el orden de D-243: los que cuelgan de otros, antes")
    void ordenDeLosModulos() {
        assertThat(borrados).extracting(b -> b.getClass().getSimpleName()).containsExactly(
                "BorradoDeCuentaEnChatAdapter", "BorradoDeCuentaEnCommunityAdapter",
                "BorradoDeCuentaEnCalendarAdapter", "BorradoDeCuentaEnNotificationsAdapter",
                "BorradoDeCuentaEnSupportAdapter", "BorradoDeCuentaEnLeadershipAdapter",
                // Spec 015 (V120): las fotos semanales de huecos de horario.
                "BorradoDeCuentaEnMentoringAdapter", "BorradoDeCuentaEnAcademyAdapter", "BorradoDeCuentaEnRagAdapter",
                "BorradoDeCuentaEnOnboardingAdapter", "BorradoDeCuentaEnPhasecontractsAdapter",
                "BorradoDeCuentaEnEvidenceAdapter", "BorradoDeCuentaEnRocksAdapter",
                "BorradoDeCuentaEnPointsAdapter", "BorradoDeCuentaEnHabitsAdapter");
    }

    @Test
    @DisplayName("de la persona no queda ninguna fila en las tablas de los módulos; de la otra, no se toca ninguna")
    void borraTodoLoDeLaPersonaYNadaDeLaOtra() {
        UUID otra = semilla.nuevaPersona();
        Map<String, Long> antes = semilla.filasPorTabla();
        Map<String, Long> deLaOtraAntes = filasDe(otra);
        UUID persona = semilla.nuevaPersona();

        borrarEnUnaTransaccion(persona);
        borrarEnUnaTransaccion(persona); // idempotente: la segunda pasada no encuentra nada ni falla

        assertThat(filasDe(persona)).as("columnas con id de la persona fuera de users")
                .allSatisfy((columna, filas) -> assertThat(filas).as(columna).isZero());
        assertThat(filasDe(otra)).as("la otra persona conserva todo").isEqualTo(deLaOtraAntes);
        Map<String, Long> despues = semilla.filasPorTabla();
        Set<String> conFilasDeMas = antes.keySet().stream()
                .filter(t -> !antes.get(t).equals(despues.get(t)))
                .collect(Collectors.toSet());
        assertThat(conFilasDeMas).as("tablas que no volvieron a su conteo previo").isEqualTo(SOBREVIVEN);
    }

    /**
     * #47 / E-658. {@code borraTodoLoDeLaPersonaYNadaDeLaOtra} fallaba al azar en el CD ({@code notificaciones.usuario_id}
     * de la otra persona: esperaba 1 y tenía 2) porque la semilla dejaba un recordatorio de evento ya vencido y sin
     * enviar: el {@code @Scheduled} que despacha recordatorios corre cada minuto en punto y, si la prueba lo cruzaba,
     * {@code notifications} le creaba un aviso a la persona sembrada.
     *
     * <p>Acá se llama a mano, y sin esperar al reloj, lo que harían los dos planificadores de calendario (el generador,
     * cada 5 minutos, y el despachador, cada minuto): lo sembrado no debe cambiar. El despachador marca
     * {@code enviado_en} ANTES de publicar el aviso, así que el cambio se ve acá mismo, sin esperar al oyente asíncrono
     * que crea la notificación.
     */
    @Test
    @DisplayName("#47: lo sembrado no lo toca ningún planificador de calendario, así que la prueba no depende del reloj")
    void lasTareasProgramadasNoTocanLoSembrado() {
        UUID persona = semilla.nuevaPersona();
        Map<String, Long> filasAntes = filasDe(persona);
        List<Map<String, Object>> recordatoriosAntes = recordatoriosDe(persona);
        assertThat(recordatoriosAntes).as("la semilla siembra un recordatorio de evento").isNotEmpty();

        generarRecordatorios.generar(Instant.now());
        despacharRecordatorios.despachar(Instant.now());

        assertThat(recordatoriosDe(persona)).as("recordatorios de la persona sembrada (enviado_en incluido)")
                .isEqualTo(recordatoriosAntes);
        assertThat(filasDe(persona)).as("filas de la persona sembrada").isEqualTo(filasAntes);
    }

    @Test
    @DisplayName("lo que la persona hizo sobre lo de otros pierde el autor y sobrevive")
    void loQueHizoSobreOtrosQuedaSinAutor() {
        UUID otra = semilla.nuevaPersona();
        UUID persona = semilla.nuevaPersona();
        jdbc.update("UPDATE renaser.asignaciones_celula SET actor_id = ? WHERE usuario_id = ?", persona, otra);
        jdbc.update("UPDATE renaser.asignaciones_curso SET asignada_por = ? WHERE usuario_id = ?", persona, otra);
        jdbc.update("UPDATE renaser.tickets_mentor SET respondido_por = ? WHERE participante_id = ?", persona, otra);
        jdbc.update("UPDATE renaser.etapas_onboarding_completadas SET marcada_por = ? WHERE usuario_id = ?",
                persona, otra);
        jdbc.update("UPDATE renaser.celulas SET mentor_id = ? WHERE mentor_id = ?", persona, otra);
        jdbc.update("UPDATE renaser.asistencias_evento SET marcado_por = ? WHERE usuario_id = ?", persona, otra);

        borrarEnUnaTransaccion(persona);

        assertThat(jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM renaser.asignaciones_celula WHERE usuario_id = ?1 AND actor_id IS NULL)
                     + (SELECT count(*) FROM renaser.asignaciones_curso WHERE usuario_id = ?1 AND asignada_por IS NULL)
                     + (SELECT count(*) FROM renaser.tickets_mentor WHERE participante_id = ?1 AND respondido_por IS NULL)
                     + (SELECT count(*) FROM renaser.etapas_onboarding_completadas WHERE usuario_id = ?1 AND marcada_por IS NULL)
                     + (SELECT count(*) FROM renaser.asistencias_evento WHERE usuario_id = ?1 AND marcado_por IS NULL)
                """.replace("?1", "'" + otra + "'"), Long.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.celulas WHERE mentor_id = ?", Long.class, persona))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.eventos WHERE creado_por = ?", Long.class, persona))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.cambios_bienvenida WHERE cambiado_por = ?",
                Long.class, persona)).isZero();
    }

    @Test
    @DisplayName("spec 005 (V109): lo que el lider escribio SOBRE la persona (persona_id) se va con ella, aunque lo reciba otro mentor")
    void lasObservacionesSobreLaPersonaSeVanConElla() {
        UUID otra = semilla.nuevaPersona();
        UUID persona = semilla.nuevaPersona();
        jdbc.update("INSERT INTO renaser.observaciones_mentor (id, mentor_id, autor_id, tipo, texto, clave_operacion, "
                + "creado_en, persona_id, semana_desde) VALUES (?, ?, ?, 'ALERTA', 'Sobre la persona', 'sobre-la-persona', "
                + "now(), ?, DATE '2026-10-10')", UUID.randomUUID(), otra, otra, persona);
        long deLaOtraAntes = semilla.filasCon("observaciones_mentor", "mentor_id", otra);

        borrarEnUnaTransaccion(persona);

        assertThat(semilla.filasCon("observaciones_mentor", "persona_id", persona)).isZero();
        assertThat(semilla.filasCon("observaciones_mentor", "mentor_id", otra)).isEqualTo(deLaOtraAntes - 1);
    }

    @Test
    @DisplayName("sin claves compartidas, ningún módulo retiene un archivo de la persona")
    void sinClavesCompartidasNadaQuedaEnUso() {
        semilla.nuevaPersona();
        UserId persona = UserId.of(semilla.nuevaPersona());

        Set<String> claves = new HashSet<>();
        borrados.forEach(b -> claves.addAll(b.archivosDe(persona)));

        assertThat(claves).containsExactlyInAnyOrder(
                "muro/fotos/" + persona + "/foto.jpg",
                "chat/" + persona + "/foto.jpg",
                "soporte/" + persona + "/adjunto.png",
                "onboarding/" + persona + "/caja/foto.jpg",
                "firmas/" + persona + "/fase1.png",
                "evidencia-habitos/" + persona + "/foto.jpg",
                "rocas/" + persona + "/foto.jpg",
                "bitacora/" + persona + "/audio.m4a",
                "santuario/" + persona + "/salida.jpg",
                "guias/" + persona + "/adjunto.pdf");
        borrados.forEach(b -> assertThat(b.archivosEnUsoTrasBorrar(persona, claves))
                .as(b.getClass().getSimpleName()).isEmpty());
    }

    private void borrarEnUnaTransaccion(UUID persona) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                borrados.forEach(b -> b.borrarDatosDe(UserId.of(persona))));
    }

    private List<Map<String, Object>> recordatoriosDe(UUID persona) {
        return jdbc.queryForList("SELECT evento_id, inicio_ocurrencia, enviar_en, enviado_en, motivo_cancelacion "
                + "FROM renaser.recordatorios_evento WHERE usuario_id = ? ORDER BY id", persona);
    }

    /** Filas por columna de persona, fuera de las tablas de {@code users}. */
    private Map<String, Long> filasDe(UUID persona) {
        return semilla.columnasDePersona().stream()
                .filter(c -> !TABLAS_DE_USERS.contains((String) c.get("tabla")))
                .collect(Collectors.toMap(c -> c.get("tabla") + "." + c.get("columna"),
                        c -> semilla.filasCon((String) c.get("tabla"), (String) c.get("columna"), persona)));
    }
}
