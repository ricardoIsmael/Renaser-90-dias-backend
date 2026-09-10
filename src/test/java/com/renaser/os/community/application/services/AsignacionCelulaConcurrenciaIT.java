package com.renaser.os.community.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.community.application.ports.out.acompanamiento.SavePoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.acompanamiento.CadenciaRotacion;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.UserId;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T09 contra Postgres real: las invariantes de acompañamiento las sostiene la BASE, no el código.
 *
 * <p><b>Por qué esto no se puede probar con mocks, ni con la comprobación a mano que había.</b>
 * El dominio ya valida antes de escribir ({@code ConjuntoAsignaciones}), pero un check-then-insert
 * pierde la carrera: entre "miré y no había nadie" y "inserto" cabe otra transacción entera. La
 * defensa real son las tres restricciones {@code EXCLUDE USING gist} de V45, y una restricción
 * solo demuestra que funciona cuando dos transacciones de verdad se pelean la misma fila.
 *
 * <p>Hasta ahora T09 estaba verificada con {@code psql} a mano: nueve casos, una tarde, ningún
 * archivo. Eso comprueba que la restricción existía ESE día. Lo que no hacía era avisar el día que
 * alguien la tocara — y un {@code ALTER TABLE ... DROP CONSTRAINT} en una migración futura no
 * rompe ninguna otra prueba de esta suite.
 *
 * <p>Sin {@code @Transactional} de clase, por el mismo motivo que
 * {@code GrabacionV90ValidacionConcurrenciaIT}: cada hilo del {@code ExecutorService} trae su
 * propia conexión y necesita ver la semilla ya comprometida. Con una transacción de test ambiente
 * no vería nada y los hilos no competirían por nada.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AsignacionCelulaConcurrenciaIT {

    /**
     * Seis intentos simultáneos. No es un número mágico: con dos, que uno gane puede ser suerte
     * del orden de arranque; con seis, cinco tienen que perder por la restricción.
     */
    private static final int INTENTOS_CONCURRENTES = 6;

    private static final Instant AHORA = Instant.parse("2026-09-10T12:00:00Z");

    @Autowired
    private SaveAsignacionPort saveAsignacionPort;
    @Autowired
    private SavePoliticaMentoriaPort savePoliticaMentoriaPort;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID cohorteId;
    private final List<UUID> celulas = new ArrayList<>();
    private final List<UUID> usuarios = new ArrayList<>();

    @BeforeEach
    void seedCohorteConDosCelulas() {
        cohorteId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.cohortes (id, nombre, fecha_inicio)
                VALUES (?, 'Cohorte de prueba T09', DATE '2026-09-01')
                """, cohorteId);
        celulas.clear();
        usuarios.clear();
        // nuevaCelula() ya la registra para la limpieza; agregarla otra vez aca dejaba la lista
        // con cada id DUPLICADO, o sea que get(0) y get(1) eran el mismo grupo. Dos pruebas
        // creian estar usando grupos distintos y no lo estaban.
        for (int i = 0; i < INTENTOS_CONCURRENTES; i++) {
            nuevaCelula("Grupo " + i);
        }
    }

    @AfterEach
    void limpiar() {
        // asignaciones_celula cae por ON DELETE CASCADE de celulas y de usuarios.
        usuarios.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", id));
        celulas.forEach(id -> jdbcTemplate.update("DELETE FROM renaser.celulas WHERE id = ?", id));
        jdbcTemplate.update("DELETE FROM renaser.politicas_mentoria WHERE cohorte_id = ?", cohorteId);
        if (cohorteId != null) {
            jdbcTemplate.update("DELETE FROM renaser.cohortes WHERE id = ?", cohorteId);
        }
    }

    private UUID nuevaCelula(String nombre) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.celulas (id, nombre, cohorte_id, tipo)
                VALUES (?, ?, ?, CAST('REGULAR' AS renaser.tipo_celula))
                """, id, nombre, cohorteId);
        celulas.add(id);
        return id;
    }

    private UserId nuevoUsuario() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture T09', CAST('APRENDIZ' AS renaser.rol_usuario), 'ACTIVO')
                """, id, id + "@renaser.test");
        usuarios.add(id);
        return UserId.of(id);
    }

    /**
     * Lanza los intentos a la vez de verdad. La {@link CyclicBarrier} existe porque sin ella los
     * hilos arrancan escalonados: el primero termina su transacción antes de que el último empiece
     * y nunca llegan a solaparse, con lo cual la prueba pasaría sin haber probado la carrera.
     */
    /**
     * Como termino un intento. Se guarda el MOTIVO del fallo y no solo que fallo: "cinco de seis
     * no entraron" tambien seria cierto si los cinco reventaran por una tabla que no existe, y la
     * prueba pasaria sin haber ejercitado ninguna restriccion.
     */
    private record Intento(boolean gano, String motivo) { }

    private List<Intento> enParalelo(List<Callable<Void>> intentos) throws Exception {
        CyclicBarrier todosListos = new CyclicBarrier(intentos.size());
        ExecutorService pool = Executors.newFixedThreadPool(intentos.size());
        List<Future<Void>> futuros;
        try {
            List<Callable<Void>> sincronizados = intentos.stream()
                    .<Callable<Void>>map(intento -> () -> {
                        todosListos.await(10, TimeUnit.SECONDS);
                        return intento.call();
                    })
                    .toList();
            futuros = pool.invokeAll(sincronizados, 60, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }
        List<Intento> resultados = new ArrayList<>(futuros.size());
        for (Future<Void> futuro : futuros) {
            try {
                futuro.get();
                resultados.add(new Intento(true, null));
            } catch (Exception fallo) {
                // Que falle es el comportamiento correcto para el perdedor. Se conserva la cadena
                // entera de causas porque el nombre de la restriccion viene en la de mas abajo.
                StringBuilder cadena = new StringBuilder();
                for (Throwable t = fallo; t != null; t = t.getCause()) {
                    cadena.append(t.getMessage()).append(' ');
                }
                resultados.add(new Intento(false, cadena.toString()));
            }
        }
        return resultados;
    }

    /** Los que ganaron. */
    private static long ganadores(List<Intento> intentos) {
        return intentos.stream().filter(Intento::gano).count();
    }

    /**
     * Que los perdedores hayan perdido EN LA BASE, y no por otra cosa.
     *
     * <p>Se aceptan dos motivos porque Postgres usa los dos, y cual toca depende de cuantos
     * lleguen a la vez. Con dos transacciones, la segunda espera al bloqueo de insercion
     * especulativa de la primera y despues choca contra la restriccion, que sale nombrada. Con
     * seis, varias quedan esperandose entre si y el detector de interbloqueos mata a las
     * sobrantes ANTES de que lleguen a chocar: el mensaje dice {@code deadlock detected} y no
     * nombra ninguna restriccion.
     *
     * <p>Las dos son Postgres negandose a que exista el segundo intervalo, que es lo que esta
     * prueba defiende. Exigir el nombre aca haria fallar la prueba por el numero de hilos y no
     * por el comportamiento -- de eso se encarga
     * {@link #laBaseNombraLaRestriccionQueRechazaCadaInvariante}, que va secuencial y si es
     * determinista.
     *
     * <p><b>Vale la pena saberlo fuera de la prueba:</b> una rafaga de operaciones simultaneas
     * sobre el mismo aprendiz o el mismo grupo aparece como interbloqueo, que es reintentable, y
     * no como "ya esta en un grupo", que no lo es. Quien maneje el error tiene que distinguirlos.
     */
    private static void perdieronEnLaBase(List<Intento> intentos, String restriccion) {
        assertThat(intentos.stream().filter(i -> !i.gano()))
                .allSatisfy(i -> assertThat(i.motivo())
                        .as("el perdedor tiene que chocar contra %s o morir por interbloqueo", restriccion)
                        .containsAnyOf(restriccion, "deadlock detected"));
    }

    /** Provoca un choque SECUENCIAL y devuelve el mensaje completo con el que la base lo rechaza. */
    private String rechazoDe(Runnable insercionQueChoca) {
        try {
            insercionQueChoca.run();
            return "";
        } catch (RuntimeException fallo) {
            StringBuilder cadena = new StringBuilder();
            for (Throwable t = fallo; t != null; t = t.getCause()) {
                cadena.append(t.getMessage()).append(' ');
            }
            return cadena.toString();
        }
    }

    private long filasVivasDe(UserId usuarioId, FuncionAcompanamiento funcion) {
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM renaser.asignaciones_celula
                WHERE usuario_id = ? AND funcion = CAST(? AS renaser.funcion_acompanamiento) AND fin IS NULL
                """, Long.class, usuarioId.value(), funcion.name());
        return total == null ? 0 : total;
    }

    private void guardar(CelulaId celulaId, UserId usuarioId, FuncionAcompanamiento funcion, String claveOperacion) {
        guardarDesde(celulaId, usuarioId, funcion, claveOperacion, AHORA);
    }

    private void guardarDesde(CelulaId celulaId, UserId usuarioId, FuncionAcompanamiento funcion,
                              String claveOperacion, Instant desde) {
        saveAsignacionPort.save(AsignacionCelula.abrir(AsignacionId.of(UUID.randomUUID()), celulaId, usuarioId,
                funcion, desde, MotivoAsignacion.ADMINISTRATIVO, null, claveOperacion));
    }

    @Test
    @DisplayName("RF-08: seis traslados simultaneos del MISMO aprendiz a grupos distintos -> queda en UNO")
    void unAprendizNoQuedaEnDosGruposAunqueLoIntentenALaVez() throws Exception {
        UserId aprendiz = nuevoUsuario();

        List<Intento> intentos = enParalelo(IntStream.range(0, INTENTOS_CONCURRENTES)
                .<Callable<Void>>mapToObj(i -> () -> {
                    guardar(CelulaId.of(celulas.get(i)), aprendiz, FuncionAcompanamiento.APRENDIZ, "op-" + i);
                    return null;
                })
                .toList());

        assertThat(ganadores(intentos))
                .as("exactamente uno de los %s puede abrir intervalo vivo", INTENTOS_CONCURRENTES)
                .isEqualTo(1);
        perdieronEnLaBase(intentos, "asignaciones_un_grupo_por_aprendiz");
        assertThat(filasVivasDe(aprendiz, FuncionAcompanamiento.APRENDIZ))
                .as("y en la base queda una sola fila viva: nadie estudia en dos grupos a la vez")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("RF-08: seis mentores simultaneos para el MISMO grupo -> lo acompana UNO")
    void unGrupoNoTerminaConDosMentoresAunqueLosAsignenALaVez() throws Exception {
        CelulaId grupo = CelulaId.of(celulas.getFirst());
        List<UserId> mentores = IntStream.range(0, INTENTOS_CONCURRENTES)
                .mapToObj(i -> nuevoUsuario())
                .toList();

        List<Intento> intentos = enParalelo(IntStream.range(0, INTENTOS_CONCURRENTES)
                .<Callable<Void>>mapToObj(i -> () -> {
                    guardar(grupo, mentores.get(i), FuncionAcompanamiento.MENTOR, "op-mentor-" + i);
                    return null;
                })
                .toList());

        assertThat(ganadores(intentos)).isEqualTo(1);
        perdieronEnLaBase(intentos, "asignaciones_un_mentor_por_celula");
        Long vivos = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM renaser.asignaciones_celula
                WHERE celula_id = ? AND funcion = CAST('MENTOR' AS renaser.funcion_acompanamiento) AND fin IS NULL
                """, Long.class, grupo.value());
        assertThat(vivos).as("un grupo, un mentor vivo").isEqualTo(1L);
    }

    /**
     * RF-29/RF-30: el outbox de Modulith reentrega eventos, y el móvil reintenta ante un timeout.
     * Repetir la MISMA operación no puede abrir un segundo intervalo — lo sostiene el índice único
     * {@code asignaciones_celula_operacion_uk} sobre (clave_operacion, célula, usuario, función).
     */
    @Test
    @DisplayName("RF-29: repetir la misma operacion seis veces a la vez no abre otro intervalo")
    void repetirLaMismaOperacionNoAbreOtroIntervalo() throws Exception {
        UserId aprendiz = nuevoUsuario();
        CelulaId grupo = CelulaId.of(celulas.getFirst());

        List<Intento> intentos = enParalelo(IntStream.range(0, INTENTOS_CONCURRENTES)
                .<Callable<Void>>mapToObj(i -> () -> {
                    guardar(grupo, aprendiz, FuncionAcompanamiento.APRENDIZ, "traslado-de-septiembre");
                    return null;
                })
                .toList());

        assertThat(ganadores(intentos)).isEqualTo(1);
        perdieronEnLaBase(intentos, "asignaciones_celula_operacion_uk");
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM renaser.asignaciones_celula WHERE usuario_id = ?",
                Long.class, aprendiz.value());
        assertThat(total).as("una sola fila, aunque la operacion llegara seis veces").isEqualTo(1L);
    }

    /**
     * El relevo exacto: A cierra en el mismo instante en que B abre. Los DOS entran.
     *
     * <p>Es la mitad que ninguna prueba de dominio puede dar. {@code PeriodoAsignacion} modela
     * {@code [inicio, fin)} en Java, pero quien decide si esas dos filas conviven es
     * {@code tstzrange(inicio, fin)} en Postgres — que también es {@code '[)'} por defecto. Si
     * alguien escribiera {@code '[]'} en una migración futura, el dominio seguiría en verde y la
     * rotación empezaría a fallar en producción: cada relevo chocaría contra la restricción y el
     * grupo se quedaría sin mentor.
     */
    @Test
    @DisplayName("El relevo exacto no solapa: A cierra donde B abre y las dos filas conviven")
    void elRelevoExactoNoSolapa() {
        CelulaId grupo = CelulaId.of(celulas.getFirst());
        UserId mentorSaliente = nuevoUsuario();
        UserId mentorEntrante = nuevoUsuario();
        Instant relevo = AHORA.plusSeconds(3600);

        AsignacionCelula saliente = AsignacionCelula.abrir(AsignacionId.of(UUID.randomUUID()), grupo,
                mentorSaliente, FuncionAcompanamiento.MENTOR, AHORA, MotivoAsignacion.ADMINISTRATIVO, null, "op-a");
        saveAsignacionPort.save(saliente);
        saliente.cerrar(relevo, MotivoAsignacion.ROTACION);
        saveAsignacionPort.save(saliente);

        // Abre EXACTAMENTE donde el otro cerro. Abrirlo en AHORA seria otra cosa —dos mentores
        // solapados— y la base lo rechaza, con razon: es el error que cometi al escribir esto.
        guardarDesde(grupo, mentorEntrante, FuncionAcompanamiento.MENTOR, "op-b", relevo);

        assertThat(filasVivasDe(mentorEntrante, FuncionAcompanamiento.MENTOR))
                .as("el entrante queda vigente")
                .isEqualTo(1);
        assertThat(filasVivasDe(mentorSaliente, FuncionAcompanamiento.MENTOR))
                .as("y el saliente ya no: su intervalo esta cerrado")
                .isZero();
    }

    /**
     * Cada invariante, contra SU restriccion, por nombre y sin concurrencia.
     *
     * <p>Es la mitad determinista de esta clase y la que de verdad avisa. Las tres pruebas de
     * arriba seguirian en verde si alguien reemplazara las restricciones por un guard en Java:
     * el aprendiz quedaria en un grupo igual. Lo que ya no pasaria es que la BASE lo rechace, y
     * ahi es donde se pierde la garantia — un guard en Java no sobrevive a dos procesos.
     *
     * <p>Un {@code DROP CONSTRAINT} o un renombre en una migracion futura rompe esto por el
     * nombre. Sin esta prueba, borrar {@code asignaciones_un_grupo_por_aprendiz} no tumbaria
     * nada en toda la suite.
     */
    @Test
    @DisplayName("Cada invariante la rechaza SU restriccion, nombrada: si alguien la borra, esto avisa")
    void laBaseNombraLaRestriccionQueRechazaCadaInvariante() {
        CelulaId grupoA = CelulaId.of(celulas.get(0));
        CelulaId grupoB = CelulaId.of(celulas.get(1));

        UserId aprendiz = nuevoUsuario();
        guardar(grupoA, aprendiz, FuncionAcompanamiento.APRENDIZ, "clave-1");
        assertThat(rechazoDe(() -> guardar(grupoB, aprendiz, FuncionAcompanamiento.APRENDIZ, "clave-2")))
                .as("un aprendiz vivo en dos grupos")
                .contains("asignaciones_un_grupo_por_aprendiz");

        UserId mentorA = nuevoUsuario();
        UserId mentorB = nuevoUsuario();
        guardar(grupoA, mentorA, FuncionAcompanamiento.MENTOR, "clave-3");
        assertThat(rechazoDe(() -> guardar(grupoA, mentorB, FuncionAcompanamiento.MENTOR, "clave-4")))
                .as("dos mentores vivos en el mismo grupo")
                .contains("asignaciones_un_mentor_por_celula");
        assertThat(rechazoDe(() -> guardar(grupoB, mentorA, FuncionAcompanamiento.MENTOR, "clave-5")))
                .as("un mentor vivo en dos grupos")
                .contains("asignaciones_una_celula_por_mentor");

        UserId otro = nuevoUsuario();
        guardar(grupoB, otro, FuncionAcompanamiento.APRENDIZ, "clave-repetida");
        assertThat(rechazoDe(() -> guardar(grupoB, otro, FuncionAcompanamiento.APRENDIZ, "clave-repetida")))
                .as("la misma operacion dos veces")
                .contains("asignaciones_celula_operacion_uk");
    }

    /**
     * Guia y soporte NO llevan exclusion, y eso es deliberado (plan.md sec. 3): pueden cubrir
     * varios grupos a la vez y ser varios por grupo. Se prueba porque una restriccion de mas
     * romperia la recepcion en silencio, y porque sin esto nadie distingue "se decidio asi" de
     * "se olvidaron".
     */
    @Test
    @DisplayName("Un guia SI puede cubrir dos grupos a la vez: la ausencia de exclusion es una decision")
    void elGuiaPuedeCubrirVariosGruposALaVez() {
        UserId guia = nuevoUsuario();

        guardar(CelulaId.of(celulas.get(0)), guia, FuncionAcompanamiento.GUIA, "guia-1");
        guardar(CelulaId.of(celulas.get(1)), guia, FuncionAcompanamiento.GUIA, "guia-2");

        Long vivos = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM renaser.asignaciones_celula
                WHERE usuario_id = ? AND funcion = CAST('GUIA' AS renaser.funcion_acompanamiento) AND fin IS NULL
                """, Long.class, guia.value());
        assertThat(vivos).as("dos grupos a la vez, y la base no se queja").isEqualTo(2L);
    }

    /**
     * La politica se guarda y se vuelve a guardar contra Postgres de verdad.
     *
     * <p>Esta prueba existe por lo que encontro su hermana: {@code AsignacionCelulaJpaEntity}
     * mandaba {@code creado_en} en NULL y fallaba toda escritura, y nadie lo veia porque los casos
     * de uso se prueban contra un doble en memoria. El adaptador de politica tenia el MISMO
     * defecto, en dos columnas — y lo unico que separaba a un administrador de un error 500 al
     * cambiar la capacidad de una cohorte era que nadie hubiera tocado ese boton todavia.
     *
     * <p>Se comprueba tambien el segundo guardado: {@code actualizado_en} no lleva
     * {@code updatable = false}, asi que ahi es donde un NULL del mapper reventaria.
     */
    @Test
    @DisplayName("La politica se guarda y se actualiza de verdad: las marcas de auditoria no van en NULL")
    void laPoliticaSeGuardaYSeActualizaContraPostgres() {
        PoliticaMentoria politica = PoliticaMentoria.porDefecto(CohorteId.of(cohorteId));

        savePoliticaMentoriaPort.save(politica);

        assertThat(marcasDeLaPolitica())
                .as("la base sella la creacion; ninguna marca queda en NULL")
                .allSatisfy(marca -> assertThat(marca).isNotNull());

        politica.reconfigurar(15, CadenciaRotacion.SEMANAL, "America/Lima", 5, 7, politica.version());
        savePoliticaMentoriaPort.save(politica);

        Integer capacidad = jdbcTemplate.queryForObject(
                "SELECT capacidad_celula FROM renaser.politicas_mentoria WHERE cohorte_id = ?",
                Integer.class, cohorteId);
        assertThat(capacidad).as("el cambio llego a la base").isEqualTo(15);
        assertThat(marcasDeLaPolitica()).allSatisfy(marca -> assertThat(marca).isNotNull());
    }

    private List<Object> marcasDeLaPolitica() {
        return jdbcTemplate.queryForList(
                        "SELECT creado_en, actualizado_en FROM renaser.politicas_mentoria WHERE cohorte_id = ?",
                        cohorteId)
                .stream()
                .flatMap(fila -> fila.values().stream())
                .toList();
    }
}
