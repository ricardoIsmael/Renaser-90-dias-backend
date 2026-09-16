package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.in.participante.ListTraineesUseCase.ResumenTraineeAdmin;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Implementacion CANONICA del patron ya probado por
 * `calendar.ConsultarProgresoParticipanteCalendarPersistenceAdapterTest`: el caso que
 * ya rompio produccion una vez (INNER JOIN hacia un ADMIN sin fila de programa
 * desapareciendo del resultado) es el primero que se cubre aca, explicitamente pedido
 * por la tarea.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ConsultarResumenParticipacionPersistenceAdapterTest {

    @Autowired
    private ConsultarResumenParticipacionPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private UserId crearUsuario(String rolCrudo, String estadoCrudo) {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST(:rol AS renaser.rol_usuario), CAST(:estado AS renaser.estado_usuario))
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .setParameter("nombre", "Fixture " + id)
                .setParameter("rol", rolCrudo)
                .setParameter("estado", estadoCrudo)
                .executeUpdate();
        return id;
    }

    private void crearParticipante(UserId id, int diaPrograma, String timezone, UUID celulaId, UserId mentorId) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone, celula_id, mentor_id)
                        VALUES (:id, :dia, :tz, :celulaId, :mentorId)
                        """)
                .setParameter("id", id.value())
                .setParameter("dia", diaPrograma)
                .setParameter("tz", timezone)
                .setParameter("celulaId", celulaId)
                .setParameter("mentorId", mentorId == null ? null : mentorId.value())
                .executeUpdate();
    }

    private UUID crearCelula(UserId mentorUsuarioId) {
        UUID cohorteId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.cohortes (id, nombre, fecha_inicio) VALUES (:id, 'Cohorte test', current_date)
                        """)
                .setParameter("id", cohorteId)
                .executeUpdate();

        entityManager.createNativeQuery("""
                        INSERT INTO renaser.perfiles_mentor (usuario_id) VALUES (:mentorId)
                        """)
                .setParameter("mentorId", mentorUsuarioId.value())
                .executeUpdate();

        UUID celulaId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.celulas (id, nombre, mentor_id, cohorte_id)
                        VALUES (:id, 'Celula test', :mentorId, :cohorteId)
                        """)
                .setParameter("id", celulaId)
                .setParameter("mentorId", mentorUsuarioId.value())
                .setParameter("cohorteId", cohorteId)
                .executeUpdate();
        return celulaId;
    }

    @Test
    void devuelveInscritoConTodosLosCamposDeUnAprendizConFilaDeParticipante() {
        UserId mentorId = crearUsuario("MENTOR", "ACTIVO");
        UUID celulaId = crearCelula(mentorId);
        UserId id = crearUsuario("APRENDIZ", "ACTIVO");
        crearParticipante(id, 20, "America/Bogota", celulaId, mentorId);

        ParticipacionPrograma resumen = adapter.resumenDe(id).orElseThrow();

        assertThat(resumen.inscrito()).isTrue();
        assertThat(resumen.diaPrograma()).isEqualTo(20);
        assertThat(resumen.zona()).isEqualTo(ZoneId.of("America/Bogota"));
        // Corregido 2026-09-08 (A-1). Aca decia PHASE_1_REBIRTH, y era una contradiccion del
        // FIXTURE: el dia 20 cae en la fase 2 (los cortes son 1-7 / 8-34 / 35-64 / 65-90), pero
        // el INSERT no escribe `fase` y la columna se quedaba en su default FASE_1_RENACER. El
        // test verificaba que el adaptador devolviera esa incoherencia. Desde que la fase se
        // DERIVA del dia —la regla que D-67 ya exige en el dominio— sale la que corresponde.
        assertThat(resumen.fase()).isEqualTo(FasePrograma.PHASE_2_DEVELOPMENT);
        assertThat(resumen.celulaId()).isEqualTo(celulaId);
        assertThat(resumen.mentorId()).isEqualTo(mentorId);
        assertThat(resumen.rol()).isEqualTo(UserRole.TRAINEE);
        assertThat(resumen.suspendido()).isFalse();
    }

    /**
     * El caso que ya rompio produccion (ver docs/BITACORA_ERRORES.md / javadoc de
     * `ConsultarProgresoParticipanteCalendarPersistenceAdapter`): un ADMIN/ALCHEMIST sin
     * fila en `participantes_programa` (el programa es opcional para su rol) NO debe
     * desaparecer del resultado con un INNER JOIN — debe aparecer con `inscrito=false`
     * y defaults seguros.
     */
    @Test
    void unAdminSinFilaDeParticipanteApareceConInscritoFalseYDefaultsSeguros() {
        UserId id = crearUsuario("ADMIN", "ACTIVO");
        // Deliberadamente SIN insertar en participantes_programa.

        ParticipacionPrograma resumen = adapter.resumenDe(id).orElseThrow();

        assertThat(resumen.inscrito()).isFalse();
        assertThat(resumen.rol()).isEqualTo(UserRole.ADMIN);
        assertThat(resumen.suspendido()).isFalse();
        assertThat(resumen.diaPrograma()).isZero();
        assertThat(resumen.celulaId()).isNull();
        assertThat(resumen.mentorId()).isNull();
        assertThat(resumen.zona()).isEqualTo(ZoneId.of("America/Lima"));
        assertThat(resumen.fase()).isEqualTo(FasePrograma.PHASE_1_REBIRTH);
    }

    @Test
    void unAlchemistSuspendidoSinFilaDeParticipanteSigueMarcandoSuspendido() {
        UserId id = crearUsuario("ALQUIMISTA", "SUSPENDIDO");

        ParticipacionPrograma resumen = adapter.resumenDe(id).orElseThrow();

        assertThat(resumen.suspendido()).isTrue();
        assertThat(resumen.inscrito()).isFalse();
    }

    @Test
    void devuelveVacioSoloCuandoElUsuarioNoExisteEnUsuarios() {
        assertThat(adapter.resumenDe(UserId.of(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void miembrosActivosDeCelulaExcluyeSuspendidos() {
        UserId mentorId = crearUsuario("MENTOR", "ACTIVO");
        UUID celulaId = crearCelula(mentorId);
        UserId activo = crearUsuario("APRENDIZ", "ACTIVO");
        UserId suspendido = crearUsuario("APRENDIZ", "SUSPENDIDO");
        crearParticipante(activo, 5, "America/Lima", celulaId, mentorId);
        crearParticipante(suspendido, 5, "America/Lima", celulaId, mentorId);

        var miembros = adapter.miembrosActivosDeCelula(celulaId);

        assertThat(miembros).containsExactly(activo);
    }

    @Test
    void contarMiembrosDeCelulaCuentaCualquierEstado() {
        UserId mentorId = crearUsuario("MENTOR", "ACTIVO");
        UUID celulaId = crearCelula(mentorId);
        UserId activo = crearUsuario("APRENDIZ", "ACTIVO");
        UserId suspendido = crearUsuario("APRENDIZ", "SUSPENDIDO");
        crearParticipante(activo, 5, "America/Lima", celulaId, mentorId);
        crearParticipante(suspendido, 5, "America/Lima", celulaId, mentorId);

        assertThat(adapter.contarMiembrosDeCelula(celulaId)).isEqualTo(2);
    }

    // ─── Consultas por rol ───────────────────────────────────────────────────
    //
    // Estas dos quedaron sin cubrir y por eso E-47 (el enum `rol_usuario` comparado contra un
    // parametro `varchar`) llego a runtime: reventaba en cada vuelta del scheduler de
    // recordatorios, no al compilar ni en la suite. El unico test que puede atrapar eso es uno
    // que EJECUTE el SQL contra Postgres de verdad, como estos.

    @Test
    void usuariosActivosConRolFiltraPorRolYPorEstado() {
        UserId aprendizActivo = crearUsuario("APRENDIZ", "ACTIVO");
        crearUsuario("APRENDIZ", "SUSPENDIDO");
        crearUsuario("MENTOR", "ACTIVO");

        var encontrados = adapter.usuariosActivosConRol(Set.of(UserRole.TRAINEE));

        assertThat(encontrados).contains(aprendizActivo);
    }

    @Test
    void usuariosActivosConRolAceptaVariosRolesALaVez() {
        UserId aprendiz = crearUsuario("APRENDIZ", "ACTIVO");
        UserId lider = crearUsuario("LIDER_MENTORES", "ACTIVO");
        UserId alquimista = crearUsuario("ALQUIMISTA", "ACTIVO");

        var encontrados = adapter.usuariosActivosConRol(Set.of(UserRole.TRAINEE, UserRole.MENTOR_LEAD));

        assertThat(encontrados).contains(aprendiz, lider).doesNotContain(alquimista);
    }

    @Test
    void usuariosActivosConRolNoConsultaSiNoHayRoles() {
        assertThat(adapter.usuariosActivosConRol(Set.of())).isEmpty();
    }

    @Test
    void usuariosActivosConDiaProgramaToleraUsuarioSinParticipacion() {
        UserId aprendiz = crearUsuario("APRENDIZ", "ACTIVO");
        UserId mentorId = crearUsuario("MENTOR", "ACTIVO");
        UUID celulaId = crearCelula(mentorId);
        crearParticipante(aprendiz, 42, "America/Lima", celulaId, mentorId);
        UserId admin = crearUsuario("ADMIN", "ACTIVO");

        var encontrados = adapter.usuariosActivosConDiaPrograma(Set.of(UserRole.TRAINEE, UserRole.ADMIN));

        // El LEFT JOIN tiene que dejar pasar al ADMIN sin fila de programa, con dia null.
        assertThat(encontrados)
                .anySatisfy(u -> {
                    assertThat(u.id()).isEqualTo(aprendiz);
                    assertThat(u.diaPrograma()).isEqualTo(42);
                })
                .anySatisfy(u -> {
                    assertThat(u.id()).isEqualTo(admin);
                    assertThat(u.diaPrograma()).isNull();
                });
    }

    // ─── El dia se DERIVA de las fechas, no de la columna (A-1, 2026-09-08) ──────────
    //
    // Antes de este cambio los cuatro casos de abajo devolvian `dia_programa` crudo, y esa
    // columna solo la escribe el barrido horario. Un backend que no estuvo arriba al cruzar la
    // medianoche del participante dejaba la app en "dia 0" TODO el dia (E-91 por otra puerta).
    //
    // El reloj de estos tests se fija a las 02:00 UTC A PROPOSITO (regla 03): en Lima eso
    // todavia es el dia ANTERIOR. Con el reloj a las 10:00 UTC —la hora que usaba media suite—
    // el dia local y el del servidor coinciden y el bug se esconde.

    /** 2026-09-08T02:00Z = 2026-09-07 21:00 en Lima, o sea "hoy" en Lima es el 7. */
    private static final Instant MADRUGADA_UTC_QUE_EN_LIMA_ES_AYER = Instant.parse("2026-09-08T02:00:00Z");
    private static final LocalDate HOY_EN_LIMA = LocalDate.parse("2026-09-07");

    private ConsultarResumenParticipacionPersistenceAdapter adapterConRelojFijo() {
        return new ConsultarResumenParticipacionPersistenceAdapter(entityManager,
                FixedClock.at(MADRUGADA_UTC_QUE_EN_LIMA_ES_AYER));
    }

    private void crearParticipanteConReloj(UserId id, int diaPrograma, LocalDate fechaInicio, boolean activado,
                                           int diasAjuste) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.participantes_programa
                            (usuario_id, dia_programa, timezone, fecha_inicio, programa_activado_en, dias_ajuste_programa)
                        VALUES (:id, :dia, 'America/Lima', :fechaInicio, :activadoEn, :ajuste)
                        """)
                .setParameter("id", id.value())
                .setParameter("dia", diaPrograma)
                .setParameter("fechaInicio", fechaInicio)
                .setParameter("activadoEn", activado ? MADRUGADA_UTC_QUE_EN_LIMA_ES_AYER.minusSeconds(86_400) : null)
                .setParameter("ajuste", diasAjuste)
                .executeUpdate();
    }

    @Test
    void elDiaSeDerivaDeLasFechasAunqueElBarridoNuncaHayaCorrido() {
        UserId id = crearUsuario("APRENDIZ", "ACTIVO");
        // Empezo AYER en su zona y la columna sigue en 0: es exactamente la fila que dejaba
        // el barrido cuando el backend estuvo caido a la medianoche de Lima.
        crearParticipanteConReloj(id, 0, HOY_EN_LIMA.minusDays(1), true, 0);

        ParticipacionPrograma resumen = adapterConRelojFijo().resumenDe(id).orElseThrow();

        assertThat(resumen.diaPrograma()).isEqualTo(2);
    }

    @Test
    void alDerivarElDiaLaFaseLoSigue() {
        UserId id = crearUsuario("APRENDIZ", "ACTIVO");
        // 34 dias transcurridos + 1 = dia 35, el primero de la fase 3. La columna `fase` sigue
        // en su default FASE_1_RENACER: si la fase se leyera de la base, este test lo detecta.
        crearParticipanteConReloj(id, 0, HOY_EN_LIMA.minusDays(34), true, 0);

        ParticipacionPrograma resumen = adapterConRelojFijo().resumenDe(id).orElseThrow();

        assertThat(resumen.diaPrograma()).isEqualTo(35);
        assertThat(resumen.fase()).isEqualTo(FasePrograma.PHASE_3_ALCHEMIST_WARRIOR);
    }

    @Test
    void losDiasDeAjusteSiguenDescontandose() {
        UserId id = crearUsuario("APRENDIZ", "ACTIVO");
        // 9 dias transcurridos + 1 = 10, menos 3 devueltos por un ADMIN (V20) = 7.
        crearParticipanteConReloj(id, 0, HOY_EN_LIMA.minusDays(9), true, 3);

        assertThat(adapterConRelojFijo().resumenDe(id).orElseThrow().diaPrograma()).isEqualTo(7);
    }

    /**
     * La contracara: mientras el reloj NO arranco manda la columna, no un 0 derivado. Es la
     * misma distincion que hace `ParticipacionPrograma.sincronizarDiaDelPrograma` — un
     * participante pre-activacion conserva el dia que un ADMIN le haya fijado a mano.
     */
    @Test
    void mientrasElProgramaNoSeActivaMandaLaColumna() {
        UserId sinActivar = crearUsuario("APRENDIZ", "ACTIVO");
        crearParticipanteConReloj(sinActivar, 5, HOY_EN_LIMA.plusDays(1), false, 0);
        UserId activadoParaManiana = crearUsuario("APRENDIZ", "ACTIVO");
        crearParticipanteConReloj(activadoParaManiana, 0, HOY_EN_LIMA.plusDays(1), true, 0);

        var adapterFijo = adapterConRelojFijo();

        assertThat(adapterFijo.resumenDe(sinActivar).orElseThrow().diaPrograma()).isEqualTo(5);
        assertThat(adapterFijo.resumenDe(activadoParaManiana).orElseThrow().diaPrograma()).isZero();
    }

    @Test
    void elPanelAdminYElBarridoPorRolTambienDerivanElDia() {
        UserId id = crearUsuario("APRENDIZ", "ACTIVO");
        crearParticipanteConReloj(id, 0, HOY_EN_LIMA.minusDays(1), true, 0);

        var adapterFijo = adapterConRelojFijo();

        assertThat(adapterFijo.listarAprendices(0, 100, null, false))
                .filteredOn(a -> a.id().equals(id))
                .singleElement()
                .satisfies(a -> assertThat(a.diaPrograma()).isEqualTo(2));
        assertThat(adapterFijo.usuariosActivosConDiaPrograma(Set.of(UserRole.TRAINEE)))
                .filteredOn(u -> u.id().equals(id))
                .singleElement()
                .satisfies(u -> assertThat(u.diaPrograma()).isEqualTo(2));
    }

    /**
     * El conteo con filtros, que es donde estaba el hueco.
     *
     * <p>Habia una prueba de {@code listarAprendices} y ninguna de {@code contarAprendices}, y el
     * WHERE es compartido: con los parametros numerados al reves, el listado pasaba —arrancaba en
     * {@code ?1}— y el conteo reventaba con {@code ParameterLabelException} porque al pegarlo
     * detras de un {@code SELECT COUNT(*)} se quedaba sin {@code ?1} ni {@code ?2}. El fallo
     * aparecio en la pantalla de Personas, no aca.
     *
     * <p>Ademas comprueba lo que hace util al contador: que lleve LOS MISMOS filtros que la lista.
     * Si contara el padron entero, la pantalla diria "1 de 340" y ofreceria paginas vacias.
     */
    @Test
    void elConteoAplicaLosMismosFiltrosQueElListado() {
        UserId mentor = crearUsuario("MENTOR", "ACTIVO");
        UUID celulaId = crearCelula(mentor);
        UserId conGrupo = crearUsuario("APRENDIZ", "ACTIVO");
        crearParticipante(conGrupo, 1, "America/Lima", celulaId, mentor);
        UserId sinGrupo = crearUsuario("APRENDIZ", "ACTIVO");
        crearParticipante(sinGrupo, 1, "America/Lima", null, null);

        long totalSinFiltro = adapter.contarAprendices(null, false);
        long totalSinGrupo = adapter.contarAprendices(null, true);

        assertThat(totalSinFiltro).isPositive();
        assertThat(totalSinGrupo).isLessThanOrEqualTo(totalSinFiltro);
        // Y el listado con el mismo filtro no devuelve mas filas que el total que anuncia.
        assertThat(adapter.listarAprendices(0, 200, null, true)).hasSizeLessThanOrEqualTo((int) totalSinGrupo);
    }

    /**
     * <b>El alta mas reciente sale en la PRIMERA tanda, aunque el abecedario la mandara al final</b>
     * (E-185, 2026-09-15).
     *
     * <p>Este es el test que hubiera atrapado el bug. El fixture es el caso exacto que lo producia:
     * dos aprendices donde el alta NUEVA se llama "Zzz" y la vieja "Aaa". Con el
     * {@code ORDER BY u.nombre_completo} anterior, pedir una sola fila devolvia a "Aaa" y el recien
     * dado de alta no aparecia hasta varias paginas despues — que es lo que el dueno vio como "no
     * cargan los usuarios nuevos".
     *
     * <p>{@code creado_en} se escribe EXPLICITO y no se deja en su default: dentro de una
     * transaccion {@code now()} es el mismo instante para las dos filas, asi que un fixture que
     * confiara en el default empataria siempre y no probaria nada.
     */
    @Test
    void elPadronEmpiezaPorElAltaMasReciente() {
        UserId vieja = crearAprendizDadoDeAltaEn("Aaa Alfabeticamente Primera", "2020-01-01T12:00:00Z");
        UserId reciente = crearAprendizDadoDeAltaEn("Zzz Alfabeticamente Ultima", "2026-09-15T12:00:00Z");

        var primeraTanda = adapter.listarAprendices(0, 1, "Alfabeticamente", false);

        assertThat(primeraTanda).extracting(ResumenTraineeAdmin::id).containsExactly(reciente);
        // Y la vieja no se pierde: sigue estando, en la pagina siguiente.
        assertThat(adapter.listarAprendices(1, 1, "Alfabeticamente", false))
                .extracting(ResumenTraineeAdmin::id).containsExactly(vieja);
    }

    /**
     * La premisa de la que depende E16 en el repo frontend
     * ({@code e2e/admin-alquimista/E15-E17-permisos-y-resiliencia.spec.ts}): paginar de verdad, sin
     * repetir ni saltear. Con {@code creado_en} EMPATADO —el caso de la siembra, que inserta a
     * todos en una transaccion— el orden lo sostiene solo el desempate por {@code u.id}.
     */
    @Test
    void conAltasEnElMismoInstanteLasPaginasSiguenSinRepetirNiSaltear() {
        String marca = "Empate" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 6; i++) {
            crearAprendizDadoDeAltaEn(marca + " " + i, "2026-09-15T12:00:00Z");
        }

        var pagina0 = adapter.listarAprendices(0, 3, marca, false);
        var pagina1 = adapter.listarAprendices(3, 3, marca, false);

        assertThat(pagina0).hasSize(3);
        assertThat(pagina1).hasSize(3);
        assertThat(pagina0).extracting(ResumenTraineeAdmin::id)
                .doesNotContainAnyElementsOf(pagina1.stream().map(ResumenTraineeAdmin::id).toList());
    }

    private UserId crearAprendizDadoDeAltaEn(String nombreCompleto, String creadoEn) {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado, creado_en)
                        VALUES (:id, :email, :nombre, CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO', CAST(:creadoEn AS timestamptz))
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .setParameter("nombre", nombreCompleto)
                .setParameter("creadoEn", creadoEn)
                .executeUpdate();
        return id;
    }

    /**
     * <b>En "Personas" salen los cinco roles; en la cola "sin grupo", solo aprendices</b>
     * (E-191, 2026-09-16).
     *
     * <p>Este es el test que hubiera atrapado el bug: el WHERE compartido arrancaba con
     * {@code u.rol = 'APRENDIZ'} y las 7 cuentas de staff no aparecian NUNCA en la pantalla. Contra
     * el codigo viejo, la primera asercion falla — el mentor no esta en el listado.
     *
     * <p>La segunda mitad fija la decision que va con el cambio: el mentor <b>no</b> se suma a
     * {@code soloSinGrupo}, que es la cola operativa "a quien hay que ubicar". A un miembro de staff
     * no se le puede asignar celula, asi que ahi seria un numero que no baja nunca.
     *
     * <p>Se filtra por una marca unica en el nombre en vez de contar el padron entero: el resto de
     * las pruebas de esta clase siembran usuarios en la misma transaccion.
     */
    @Test
    void elPadronTraeTodosLosRolesYLaColaSinGrupoSoloAprendices() {
        String marca = "Padron" + UUID.randomUUID().toString().substring(0, 8);
        UserId mentor = crearUsuarioLlamado("MENTOR", marca + " Mentor Sin Programa");
        UserId aprendiz = crearUsuarioLlamado("APRENDIZ", marca + " Aprendiz Sin Grupo");
        crearParticipante(aprendiz, 1, "America/Lima", null, null);

        assertThat(adapter.listarAprendices(0, 20, marca, false))
                .extracting(ResumenTraineeAdmin::id)
                .containsExactlyInAnyOrder(mentor, aprendiz);
        assertThat(adapter.listarAprendices(0, 20, marca, false))
                .filteredOn(fila -> fila.id().equals(mentor))
                .singleElement()
                .satisfies(fila -> assertThat(fila.rol()).isEqualTo(UserRole.MENTOR));

        assertThat(adapter.listarAprendices(0, 20, marca, true))
                .extracting(ResumenTraineeAdmin::id)
                .containsExactly(aprendiz);
        assertThat(adapter.contarAprendices(marca, true)).isEqualTo(1);
        assertThat(adapter.contarAprendices(marca, false)).isEqualTo(2);
    }

    private UserId crearUsuarioLlamado(String rolCrudo, String nombreCompleto) {
        UserId id = UserId.of(UUID.randomUUID());
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, :nombre, CAST(:rol AS renaser.rol_usuario), 'ACTIVO')
                        """)
                .setParameter("id", id.value())
                .setParameter("email", id + "@renaser.test")
                .setParameter("nombre", nombreCompleto)
                .setParameter("rol", rolCrudo)
                .executeUpdate();
        return id;
    }

    /** Una busqueda que no coincide con nadie devuelve cero, y no revienta la consulta. */
    @Test
    void laBusquedaSinCoincidenciasDevuelveCeroEnLosDos() {
        String imposible = "zzz-no-existe-" + UUID.randomUUID();

        assertThat(adapter.contarAprendices(imposible, false)).isZero();
        assertThat(adapter.listarAprendices(0, 20, imposible, false)).isEmpty();
    }
}
