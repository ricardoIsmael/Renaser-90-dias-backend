package com.renaser.os.habits.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase;
import com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase.CompletarClaseDiariaHabitoCommand;
import com.renaser.os.habits.application.ports.in.espiritu.CompletarPastillaRenacerUseCase;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
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
 * Regresion de los DOS HERMANOS del hallazgo de seguridad del 2026-09-21, el mismo que
 * {@code PostDiarioComunidadCerrojoIT} cubre para el post diario en comunidad: <i>el servicio
 * cargaba el registro del habito SIN cerrojo y recien despues llamaba a {@code completar()},
 * dentro de una sola transaccion</i>. Hibernate 7.4.5.Final no rehidrata una entidad ya
 * gestionada cuando la vuelve a traer una consulta con {@code @Lock(PESSIMISTIC_WRITE)}
 * ({@code EntityInitializerImpl.resolveEntityInstance1} la marca {@code INITIALIZED},
 * {@code initializeInstance} solo hidrata en {@code RESOLVED}, y {@code upgradeLockMode} se
 * limita a anotar el {@code LockMode} en el {@code EntityEntry}), asi que el cerrojo se tomaba
 * pero la decision se seguia tomando sobre el estado viejo — y se pagaba dos veces.
 *
 * <p><b>Por que Postgres real y no mocks.</b> Lo que falla no es una rama del codigo sino el
 * contexto de persistencia: hacen falta una transaccion de verdad, un {@code SELECT ... FOR
 * UPDATE} de verdad y varios hilos peleandose la fila. Con puertos falsos los dos tests
 * unitarios de estos servicios pasaban igual con el codigo roto.
 *
 * <p><b>Cada hilo trae su propia transaccion, y no es decoracion.</b> Ninguno de los dos
 * servicios lleva {@code @Transactional} propio — se la pone su llamador —, asi que envolverlos
 * en un {@link TransactionTemplate} es lo que reproduce la forma real: UNA transaccion y UN
 * contexto de persistencia compartidos por la busqueda del registro y por el {@code completar()}
 * que la sigue. Sin esa envoltura cada consulta abriria su propio contexto y el hallazgo no
 * existiria.
 *
 * <p><b>Los dos hermanos no se envuelven igual, a proposito</b>, porque no corren igual:
 *
 * <ul>
 *   <li><b>Clase Diaria</b> corre dentro del {@code @Transactional} de
 *       {@code academy.ClaseDiariaService.completar}: una transaccion, y punto.</li>
 *   <li><b>Pastilla Renacer</b> corre dentro de la transaccion PROPIA (REQUIRES_NEW) que abre
 *       {@code EspirituService.reflejarEnPastillaRenacer}, anidada en el
 *       {@code @Transactional} de {@code EspirituService.entregar}. Se reproducen las dos, una
 *       adentro de la otra, justamente para dejar demostrado que el REQUIRES_NEW <b>no</b>
 *       arregla nada: aisla la entrega del resumen de Espiritu de un fallo del habito, pero las
 *       dos lecturas del registro siguen cayendo en la MISMA transaccion (la nueva), porque
 *       {@code RegistroService.completar} es REQUIRED y se une a ella. Mueve de lugar la
 *       transaccion que comparten; no las separa.</li>
 * </ul>
 *
 * <p><b>Sin {@code @Transactional} de clase</b>, por el mismo motivo que
 * {@code AsignacionCelulaConcurrenciaIT}: cada hilo trae su propia conexion y necesita ver la
 * semilla ya comprometida.
 *
 * <p><b>Usa los habitos REALES del catalogo</b> (los que siembra V4), porque {@code clave_sistema}
 * es UNIQUE y es por donde los dos casos de uso los buscan: no se puede poner otro al lado. Eso
 * ata la prueba a los horarios de V4 —Clase Diaria dispara 14:59 sin hora limite, Pastilla
 * Renacer va de 07:00 a 12:00—, asi que <b>el reloj va fijo</b>: a las 10:00Z del 2026-08-24, que
 * en Lima son las 05:00, las dos anclas quedan por delante y los dos habitos pagan sus 10 puntos
 * completos ({@code FaseOtorgamiento.A_TIEMPO}) corra la suite a la hora que corra. Con el reloj
 * del sistema el puntaje dependeria de la hora del dia y el test pasaria o fallaria por motivos
 * ajenos al cerrojo. El reloj entra por el puerto {@code Clock} justo para esto (CLAUDE.MD §5).
 *
 * <p><b>Que se comprueba es la PLATA.</b> El dano del hallazgo es el doble pago, asi que las
 * aserciones que mandan son el saldo de liga y la cantidad de asientos en el ledger, no el
 * estado de la fila: contra el codigo anterior el registro tambien terminaba en
 * {@code COMPLETADO} — solo que despues de haberse cobrado varias veces.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, ClaseDiariaYPastillaCerrojoIT.RelojFijoConfig.class})
class ClaseDiariaYPastillaCerrojoIT {

    /**
     * Seis cierres simultaneos. Mismo numero que {@code PostDiarioComunidadCerrojoIT} y que la
     * regresion del cerrojo de {@code completar}, y por el mismo motivo: con dos, que uno gane
     * puede ser suerte del orden de arranque.
     */
    private static final int INTENTOS_CONCURRENTES = 6;

    /** 10:00Z = 05:00 en Lima: por delante del ancla de los dos habitos (ver javadoc de la clase). */
    private static final Instant AHORA = Instant.parse("2026-08-24T10:00:00Z");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** El dia de {@link #AHORA} en Lima — el mismo "hoy" que van a calcular los dos servicios. */
    private static final LocalDate DIA = AHORA.atZone(LIMA).toLocalDate();
    /**
     * Dentro del rango de los dos horarios de V4: Clase Diaria rige del dia 1 al 90, Pastilla
     * Renacer del 8 en adelante.
     */
    private static final int DIA_PROGRAMA = 20;

    /** {@code puntajes_participante.puntos_liga} arranca en 100 (V1) y cada habito vale 10. */
    private static final int SALDO_INICIAL = 100;
    private static final int PUNTOS_DEL_HABITO = 10;

    /** Minimo 15 caracteres: lo valida el constructor del comando de la Clase Diaria. */
    private static final String RESUMEN_CLASE = "Hoy entendi que la disciplina no es castigo";
    private static final String RESUMEN_AUDIO = "Lo que interprete del audio de hoy";

    @Autowired
    private CompletarClaseDiariaHabitoUseCase completarClaseDiaria;
    @Autowired
    private CompletarPastillaRenacerUseCase completarPastillaRenacer;
    @Autowired
    private SaveRegistroHabitoPort saveRegistroPort;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /** La transaccion propia de {@code EspirituService.reflejarEnPastillaRenacer}. */
    private TransactionTemplate transaccionPropia;

    private UserId participanteId;

    @BeforeEach
    void seedFixtures() {
        transaccionPropia = new TransactionTemplate(transactionManager);
        transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());

        participanteId = UserId.of(UUID.randomUUID());
        jdbcTemplate.update("""
                INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                VALUES (?, ?, 'Fixture cerrojo hermanos', 'APRENDIZ', 'ACTIVO')
                """, participanteId.value(), participanteId + "@renaser.test");
        jdbcTemplate.update("""
                INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, timezone)
                VALUES (?, ?, 'America/Lima')
                """, participanteId.value(), DIA_PROGRAMA);
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra participantes_programa, registros_habito,
        // puntajes_participante y ajustes_puntos_liga. Los habitos NO se borran: son catalogo
        // compartido y esta prueba solo los leyo.
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", participanteId.value());
    }

    private HabitoId habitoDeCatalogo(String claveSistema) {
        return HabitoId.of(jdbcTemplate.queryForObject(
                "SELECT id FROM renaser.habitos WHERE clave_sistema = ?", UUID.class, claveSistema));
    }

    private RegistroHabitoId seedRegistroPendiente(HabitoId habitoId) {
        RegistroHabitoId id = RegistroHabitoId.of(UUID.randomUUID());
        saveRegistroPort.save(RegistroHabito.generar(id, participanteId, habitoId, DIA, DIA_PROGRAMA,
                TipoDia.delDia(DIA), false, AHORA));
        return id;
    }

    // ─── Los dos caminos, con la transaccion que en produccion pone cada llamador ──────────

    /** Como lo llama {@code academy.ClaseDiariaService.completar}: una sola transaccion REQUIRED. */
    private CompletarClaseDiariaHabitoUseCase.RegistroCompletado cerrarComoAcademy() {
        return transactionTemplate.execute(status -> completarClaseDiaria.completarDeHoy(
                new CompletarClaseDiariaHabitoCommand(participanteId, RESUMEN_CLASE)));
    }

    /**
     * Como lo llama {@code EspirituService}: la transaccion de {@code entregar()} por fuera y la
     * propia (REQUIRES_NEW) de {@code reflejarEnPastillaRenacer} por dentro. Ver javadoc de la
     * clase para por que la anidacion importa.
     */
    private CompletarPastillaRenacerUseCase.RegistroCompletado reflejarComoEspiritu() {
        return transactionTemplate.execute(deEntregar ->
                transaccionPropia.execute(delEspejo ->
                        completarPastillaRenacer.completarDeHoy(participanteId, RESUMEN_AUDIO).orElseThrow()));
    }

    // ─── Aserciones compartidas ────────────────────────────────────────────────────────────

    private String estadoDe(RegistroHabitoId id) {
        return jdbcTemplate.queryForObject("SELECT estado FROM renaser.registros_habito WHERE id = ?", String.class,
                id.value());
    }

    private int puntosOtorgadosDe(RegistroHabitoId id) {
        Integer puntos = jdbcTemplate.queryForObject(
                "SELECT puntos_otorgados FROM renaser.registros_habito WHERE id = ?", Integer.class, id.value());
        return puntos == null ? 0 : puntos;
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

    private void exigirUnSoloPago(RegistroHabitoId id) {
        assertThat(estadoDe(id)).isEqualTo("COMPLETADO");
        assertThat(puntosOtorgadosDe(id)).isEqualTo(PUNTOS_DEL_HABITO);
        assertThat(puntosLiga())
                .as("una sola accion del aprendiz paga una sola vez, sin importar cuantas entregas concurran")
                .isEqualTo(SALDO_INICIAL + PUNTOS_DEL_HABITO);
        assertThat(asientosDelLedger()).as("un solo asiento en el ledger de puntos").isEqualTo(1);
    }

    // ─── Clase Diaria ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Clase Diaria: un cierre normal (sin concurrencia) sigue cerrando el habito y pagando una vez")
    void claseDiariaCierreNormalSigueFuncionando() {
        RegistroHabitoId id = seedRegistroPendiente(
                habitoDeCatalogo(CompletarClaseDiariaHabitoUseCase.CLAVE_SISTEMA_DAILY_CLASS));

        var resultado = cerrarComoAcademy();

        assertThat(resultado.registroId()).isEqualTo(id.value());
        assertThat(resultado.puntosOtorgados()).isEqualTo(PUNTOS_DEL_HABITO);
        exigirUnSoloPago(id);
        assertThat(jdbcTemplate.queryForObject("SELECT respuesta_texto FROM renaser.registros_habito WHERE id = ?",
                String.class, id.value())).isEqualTo(RESUMEN_CLASE);
    }

    /**
     * El unico camino concurrente de la Clase Diaria es este mismo endpoint repetido — un doble
     * toque, o el reenvio tras un corte de red —, porque {@code PoliticaClaseDiaria} le cierra a
     * {@code DAILY_CLASS} la ruta generica {@code POST /habit-tracks/{id}/complete}. Alcanza: la
     * carrera contra si mismo pagaba igual de mal.
     *
     * <p>Los seis tienen que devolver LO MISMO, y eso es la mitad del contrato: el que pierde la
     * carrera cae en la rama idempotente de {@code ClaseDiariaHabitoService.yaCompletadaHoy} y
     * devuelve el resultado ya otorgado. Contra el codigo anterior tambien devolvian todos 200,
     * pero porque los seis habian completado y cobrado de verdad.
     */
    @Test
    @DisplayName("Clase Diaria: 6 entregas simultaneas cierran el habito UNA vez y pagan UNA vez "
            + "(contra el codigo anterior: un pago por entrega, porque el cerrojo llegaba despues de la decision)")
    void claseDiariaEntregasSimultaneasPaganUnaSolaVez() throws InterruptedException {
        RegistroHabitoId id = seedRegistroPendiente(
                habitoDeCatalogo(CompletarClaseDiariaHabitoUseCase.CLAVE_SISTEMA_DAILY_CLASS));

        List<CompletarClaseDiariaHabitoUseCase.RegistroCompletado> resultados = correrEnParalelo(
                IntStream.range(0, INTENTOS_CONCURRENTES)
                        .<Callable<CompletarClaseDiariaHabitoUseCase.RegistroCompletado>>mapToObj(
                                i -> this::cerrarComoAcademy)
                        .toList());

        exigirUnSoloPago(id);
        assertThat(resultados).allSatisfy(resultado -> {
            assertThat(resultado.registroId()).isEqualTo(id.value());
            assertThat(resultado.puntosOtorgados()).isEqualTo(PUNTOS_DEL_HABITO);
        });
    }

    // ─── Pastilla Renacer ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Pastilla Renacer: un espejo normal (sin concurrencia) sigue cerrando el habito y pagando una vez")
    void pastillaCierreNormalSigueFuncionando() {
        RegistroHabitoId id = seedRegistroPendiente(
                habitoDeCatalogo(CompletarPastillaRenacerUseCase.CLAVE_SISTEMA_PASTILLA_RENACER));

        var resultado = reflejarComoEspiritu();

        assertThat(resultado.registroId()).isEqualTo(id.value());
        assertThat(resultado.puntosOtorgados()).isEqualTo(PUNTOS_DEL_HABITO);
        exigirUnSoloPago(id);
        assertThat(jdbcTemplate.queryForObject("SELECT respuesta_texto FROM renaser.registros_habito WHERE id = ?",
                String.class, id.value())).isEqualTo(RESUMEN_AUDIO);
    }

    /**
     * Aca los caminos concurrentes son DOS de verdad y no uno repetido: este habito no tiene una
     * politica que le cierre la ruta generica (no existe una {@code PoliticaPastillaRenacer}, a
     * diferencia de {@code PoliticaClaseDiaria}), asi que la entrega del resumen de Espiritu y el
     * {@code POST /habit-tracks/{id}/complete} de siempre pueden cerrar el mismo registro a la
     * vez. Se modela con seis espejos simultaneos, que es la misma carrera y no depende del
     * modulo de rutas.
     *
     * <p>Y va anidado en una transaccion externa a proposito: es la forma real, y deja
     * demostrado que el REQUIRES_NEW de {@code EspirituService} no era el que salvaba esto.
     */
    @Test
    @DisplayName("Pastilla Renacer: 6 espejos simultaneos cierran el habito UNA vez y pagan UNA vez, "
            + "pese al REQUIRES_NEW (que aisla la entrega de Espiritu, no las dos lecturas entre si)")
    void pastillaEspejosSimultaneosPaganUnaSolaVez() throws InterruptedException {
        RegistroHabitoId id = seedRegistroPendiente(
                habitoDeCatalogo(CompletarPastillaRenacerUseCase.CLAVE_SISTEMA_PASTILLA_RENACER));

        List<CompletarPastillaRenacerUseCase.RegistroCompletado> resultados = correrEnParalelo(
                IntStream.range(0, INTENTOS_CONCURRENTES)
                        .<Callable<CompletarPastillaRenacerUseCase.RegistroCompletado>>mapToObj(
                                i -> this::reflejarComoEspiritu)
                        .toList());

        exigirUnSoloPago(id);
        assertThat(resultados).allSatisfy(resultado -> {
            assertThat(resultado.registroId()).isEqualTo(id.value());
            assertThat(resultado.puntosOtorgados()).isEqualTo(PUNTOS_DEL_HABITO);
        });
    }

    // ─── Plomeria ──────────────────────────────────────────────────────────────────────────

    private <T> List<T> correrEnParalelo(List<Callable<T>> intentos) throws InterruptedException {
        // La barrera alinea el arranque: sin ella el primer hilo puede llegar a commitear antes
        // de que el ultimo haya empezado, y entonces no habria carrera que probar.
        CyclicBarrier salida = new CyclicBarrier(intentos.size());
        List<Callable<T>> alineados = intentos.stream()
                .<Callable<T>>map(intento -> () -> {
                    salida.await(30, TimeUnit.SECONDS);
                    return intento.call();
                })
                .toList();
        ExecutorService pool = Executors.newFixedThreadPool(intentos.size());
        List<Future<T>> resultados;
        try {
            resultados = pool.invokeAll(alineados, 60, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }
        return resultados.stream().map(ClaseDiariaYPastillaCerrojoIT::<T>exigirQueNoExplote).toList();
    }

    private static <T> T exigirQueNoExplote(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException("Un cierre concurrente fallo con una excepcion inesperada", e);
        }
    }

    @TestConfiguration
    static class RelojFijoConfig {

        /** Ver javadoc de la clase: fija el puntaje de los dos habitos en 10 a cualquier hora. */
        @Bean
        @Primary
        Clock relojFijo() {
            return FixedClock.at(AHORA);
        }
    }
}
