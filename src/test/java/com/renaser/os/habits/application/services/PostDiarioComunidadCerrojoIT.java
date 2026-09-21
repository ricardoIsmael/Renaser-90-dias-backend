package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.politica.PoliticaPostDiarioComunidad;
import com.renaser.os.habits.application.ports.in.registro.CerrarPostDiarioComunidadUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Regresion del hallazgo de seguridad del 2026-09-21: <i>el oyente que cierra el habito de post
 * diario cargaba el registro SIN cerrojo antes de pedirlo CON cerrojo en la misma transaccion, y
 * Hibernate le devolvia la instancia vieja</i>.
 *
 * <p><b>Por que tiene que ser una prueba de integracion, y con Postgres real.</b> Lo que falla no
 * es una rama del codigo sino el contexto de persistencia: las dos lecturas viven en la misma
 * transaccion —la que abre el {@code @ApplicationModuleListener}— y, cuando la consulta con
 * {@code @Lock(PESSIMISTIC_WRITE)} vuelve a traer una fila cuya entidad el contexto ya tiene,
 * Hibernate 7.4.5.Final no la rehidrata: {@code EntityInitializerImpl.resolveEntityInstance1} la
 * marca {@code INITIALIZED}, {@code initializeInstance} solo hidrata en {@code RESOLVED} y no
 * hace nada, y {@code upgradeLockMode} se limita a anotar el {@code LockMode} en el
 * {@code EntityEntry}. Con puertos falsos eso no existe: {@code PostDiarioComunidadHabitoServiceTest}
 * ya tiene un test llamado "ni segunda publicacion ni reentrega pagan dos veces" y pasaba
 * igual con el codigo roto, porque el mock devolvia a mano un registro ya COMPLETADO. Hace falta
 * una transaccion de verdad, un {@code SELECT ... FOR UPDATE} de verdad y dos hilos peleandose la
 * fila.
 *
 * <p><b>Cada hilo abre su propia transaccion a proposito.</b> {@code alPublicarEnElMuro} no lleva
 * {@code @Transactional} —en produccion la transaccion la pone el oyente— asi que envolverlo en
 * un {@link TransactionTemplate} es lo que reproduce la forma real: una sola transaccion, un solo
 * contexto de persistencia, para la busqueda del registro y para el {@code completar()} que la
 * sigue. Sin esa envoltura cada consulta abriria su propio contexto y el hallazgo no existiria.
 *
 * <p><b>Sin {@code @Transactional} de clase</b>, por el mismo motivo que
 * {@code AsignacionCelulaConcurrenciaIT}: cada hilo trae su propia conexion y necesita ver la
 * semilla ya comprometida.
 *
 * <p><b>Usa el habito REAL del catalogo</b> (el que V4 siembra y V24 marca con
 * {@code clave_sistema = COMMUNITY_POST}), porque esa clave es UNIQUE y es la que el caso de uso
 * busca: no se puede insertar otro al lado. Ese habito trae su horario de V4 —disparo 22:00, sin
 * hora limite—, asi que el dia del registro se elige MANANA en Lima: el ancla queda siempre en el
 * futuro contra el reloj real del contexto, el otorgamiento cae en {@code A_TIEMPO} y el habito
 * paga sus 10 puntos completos corra la prueba a la hora que corra.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PostDiarioComunidadCerrojoIT {

    /**
     * Seis cierres simultaneos. Es el mismo numero que usa la regresion del cerrojo de
     * {@code completar}, y por el mismo motivo: con dos, que uno gane puede ser suerte del orden
     * de arranque.
     */
    private static final int INTENTOS_CONCURRENTES = 6;

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Manana en Lima: deja el ancla de las 22:00 siempre por delante del reloj real (ver javadoc). */
    private static final LocalDate DIA = LocalDate.now(LIMA).plusDays(1);
    /** Mediodia de ese dia en Lima, o sea bien dentro de la ventana [DIA 00:00, DIA+1 00:00). */
    private static final Instant PUBLICADO_EN = DIA.atTime(12, 0).atZone(LIMA).toInstant();

    /** {@code puntajes_participante.puntos_liga} arranca en 100 (V1) y el habito vale 10. */
    private static final int SALDO_INICIAL = 100;
    private static final int PUNTOS_DEL_HABITO = 10;

    @Autowired
    private CerrarPostDiarioComunidadUseCase cerrarPostDiarioUseCase;
    @Autowired
    private CompletarRegistroUseCase completarUseCase;
    @Autowired
    private SaveRegistroHabitoPort saveRegistroPort;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private UserId participanteId;
    private HabitoId habitoId;

    @BeforeEach
    void seedFixtures() {
        participanteId = UserId.of(UUID.randomUUID());

        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture cerrojo', 'APRENDIZ', 'ACTIVO')
                """, participanteId.value(), participanteId + "@renaser.test");
        jdbcTemplate.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone)
                VALUES (?, 5, 'America/Lima')
                """, participanteId.value());
        // El habito del catalogo, no uno de mentira: clave_sistema es UNIQUE y es por donde el
        // caso de uso lo busca, asi que no hay lugar para un segundo COMMUNITY_POST.
        habitoId = HabitoId.of(jdbcTemplate.queryForObject(
                "SELECT id FROM renaser.habitos WHERE clave_sistema = ?", UUID.class,
                PoliticaPostDiarioComunidad.CLAVE_SISTEMA));
        // La publicacion tiene que existir DENTRO del dia local del registro: es lo que
        // PoliticaPostDiarioComunidad vuelve a comprobar contra publicaciones_muro.
        jdbcTemplate.update("""
                INSERT INTO renaser.publicaciones_muro (id, autor_id, tipo, texto, creado_en)
                VALUES (?, ?, 'MANUAL', 'Hoy si', ?)
                """, UUID.randomUUID(), participanteId.value(), java.sql.Timestamp.from(PUBLICADO_EN));
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa, registros_habito,
        // publicaciones_muro, puntajes_participante y ajustes_puntos_liga. El habito NO se borra:
        // es catalogo compartido y esta prueba solo lo leyo.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId.value());
    }

    private RegistroHabitoId seedRegistroPendiente() {
        RegistroHabitoId id = RegistroHabitoId.of(UUID.randomUUID());
        saveRegistroPort.save(RegistroHabito.generar(id, participanteId, habitoId, DIA, 5, TipoDia.delDia(DIA), false,
                PUBLICADO_EN));
        return id;
    }

    /** El camino del oyente, con la transaccion que en produccion pone {@code @ApplicationModuleListener}. */
    private void cerrarComoElOyente() {
        transactionTemplate.executeWithoutResult(status ->
                cerrarPostDiarioUseCase.alPublicarEnElMuro(participanteId, PUBLICADO_EN));
    }

    private int puntosLiga() {
        Integer saldo = jdbcTemplate.queryForObject(
                "SELECT puntos_liga FROM renaser.puntajes_participante WHERE participante_id = ?", Integer.class,
                participanteId.value());
        return saldo == null ? SALDO_INICIAL : saldo;
    }

    private int asientosDelLedger() {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM renaser.ajustes_puntos_liga WHERE participante_id = ?", Integer.class,
                participanteId.value());
        return total == null ? 0 : total;
    }

    @Test
    @DisplayName("un cierre normal (sin concurrencia) sigue cerrando el habito y pagando una vez")
    void cierreNormalSigueFuncionando() {
        RegistroHabitoId id = seedRegistroPendiente();

        cerrarComoElOyente();

        assertThat(jdbcTemplate.queryForObject("SELECT estado FROM renaser.registros_habito WHERE id = ?", String.class,
                id.value())).isEqualTo("COMPLETADO");
        assertThat(puntosLiga()).isEqualTo(SALDO_INICIAL + PUNTOS_DEL_HABITO);
        assertThat(asientosDelLedger()).isEqualTo(1);
    }

    @Test
    @DisplayName("6 publicaciones simultaneas cierran el habito UNA vez y pagan UNA vez "
            + "(contra el codigo anterior: 6 pagos, porque el cerrojo llegaba despues de la decision)")
    void publicacionesSimultaneasNoPaganDosVeces() throws InterruptedException {
        RegistroHabitoId id = seedRegistroPendiente();

        correrEnParalelo(IntStream.range(0, INTENTOS_CONCURRENTES)
                .<Callable<Void>>mapToObj(i -> () -> {
                    cerrarComoElOyente();
                    return null;
                })
                .toList());

        assertThat(jdbcTemplate.queryForObject("SELECT estado FROM renaser.registros_habito WHERE id = ?", String.class,
                id.value())).isEqualTo("COMPLETADO");
        assertThat(jdbcTemplate.queryForObject("SELECT puntos_otorgados FROM renaser.registros_habito WHERE id = ?",
                Integer.class, id.value())).isEqualTo(PUNTOS_DEL_HABITO);
        assertThat(puntosLiga())
                .as("una sola accion del aprendiz paga una sola vez, sin importar cuantas entregas concurran")
                .isEqualTo(SALDO_INICIAL + PUNTOS_DEL_HABITO);
        assertThat(asientosDelLedger()).as("un solo asiento en el ledger de puntos").isEqualTo(1);
    }

    /**
     * La otra mitad del hallazgo, y la que no es de puntos: el comando del oyente manda
     * {@code respuestaTexto} y {@code calificacionProductividad} NULOS, asi que la actualizacion
     * perdida no solo pagaba de mas — tambien pisaba con {@code null} lo que el aprendiz habia
     * escrito por el camino HTTP, que es el que el cliente movil dispara junto con la publicacion.
     *
     * <p>La comprobacion es sobre quien gano: el que reporta exito es el que tiene que quedar
     * escrito. Contra el codigo anterior el {@code POST /complete} devolvia 200 con su texto y un
     * oyente que habia leido PENDIENTE antes lo sobreescribia despues con nulos.
     */
    @Test
    @DisplayName("el POST /complete con texto y las publicaciones simultaneas no se pisan: "
            + "gana uno solo y lo que ese escribio es lo que queda")
    void laRespuestaDelAprendizNoSeBorra() throws InterruptedException {
        RegistroHabitoId id = seedRegistroPendiente();
        var httpGano = new java.util.concurrent.atomic.AtomicBoolean(false);

        List<Callable<Void>> intentos = new java.util.ArrayList<>();
        intentos.add(() -> {
            try {
                // El camino HTTP: su propia transaccion, con el texto y la calificacion del aprendiz.
                transactionTemplate.executeWithoutResult(status -> completarUseCase.completar(
                        new CompletarRegistroCommand(participanteId, id, "Lo escribi yo", 8)));
                httpGano.set(true);
            } catch (IllegalStateException perdioLaCarrera) {
                // 409: otro camino lo cerro primero. Es el contrato de hoy y se conserva.
                httpGano.set(false);
            }
            return null;
        });
        IntStream.range(1, INTENTOS_CONCURRENTES).forEach(i -> intentos.add(() -> {
            cerrarComoElOyente();
            return null;
        }));

        correrEnParalelo(intentos);

        // El texto PRIMERO, y no por estilo: es la mitad del hallazgo que no es de puntos, y si
        // se comprueba despues del saldo nunca llega a evaluarse cuando el doble pago ya fallo.
        String texto = jdbcTemplate.queryForObject(
                "SELECT respuesta_texto FROM renaser.registros_habito WHERE id = ?", String.class, id.value());
        Integer calificacion = jdbcTemplate.queryForObject(
                "SELECT calificacion_productividad FROM renaser.registros_habito WHERE id = ?", Integer.class,
                id.value());
        if (httpGano.get()) {
            assertThat(texto).as("si el POST /complete devolvio exito, su texto no lo puede borrar nadie despues")
                    .isEqualTo("Lo escribi yo");
            assertThat(calificacion).isEqualTo(8);
        } else {
            assertThat(texto).as("si el POST /complete perdio la carrera, nunca llego a escribir").isNull();
            assertThat(calificacion).isNull();
        }

        assertThat(puntosLiga()).as("un solo pago, gane quien gane").isEqualTo(SALDO_INICIAL + PUNTOS_DEL_HABITO);
        assertThat(asientosDelLedger()).isEqualTo(1);
    }

    private void correrEnParalelo(List<Callable<Void>> intentos) throws InterruptedException {
        // La barrera alinea el arranque: sin ella el primer hilo puede llegar a commitear antes
        // de que el ultimo haya empezado, y entonces no habria carrera que probar.
        CyclicBarrier salida = new CyclicBarrier(intentos.size());
        List<Callable<Void>> alineados = intentos.stream()
                .<Callable<Void>>map(intento -> () -> {
                    salida.await(30, TimeUnit.SECONDS);
                    return intento.call();
                })
                .toList();
        ExecutorService pool = Executors.newFixedThreadPool(intentos.size());
        List<Future<Void>> resultados;
        try {
            resultados = pool.invokeAll(alineados, 60, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }
        resultados.forEach(PostDiarioComunidadCerrojoIT::exigirQueNoExplote);
    }

    private static void exigirQueNoExplote(Future<Void> future) {
        try {
            future.get();
        } catch (Exception e) {
            throw new IllegalStateException("Un cierre concurrente fallo con una excepcion inesperada", e);
        }
    }
}
