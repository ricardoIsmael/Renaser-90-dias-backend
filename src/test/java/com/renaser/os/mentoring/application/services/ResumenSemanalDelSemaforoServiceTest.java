package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.mentoring.api.ResumenSemanalDelGrupoEvent;
import com.renaser.os.mentoring.api.ResumenSemanalGeneralEvent;
import com.renaser.os.mentoring.domain.model.resumen.ReglasDelResumenSemanal;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DetalleDelSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El barrido del resumen del sábado, sin Spring. Lo que se verifica acá y no en las reglas puras es
 * el cableado: la consulta en lote por grupo, el mentor fuera de su propio semáforo, la transacción
 * propia de cada evento (E-250) y que un grupo que falla no frene a los demás.
 *
 * <p>Relojes a las 04:30 y 05:40 UTC de un sábado, a los dos lados de la medianoche de Lima
 * (regla 02 §3).
 */
class ResumenSemanalDelSemaforoServiceTest {

    /** Sábado 26 de setiembre de 2026, 05:40 UTC = 00:40 en Lima: primera corrida de la ventana. */
    private static final Instant SABADO_0040_LIMA = Instant.parse("2026-09-26T05:40:00Z");
    /** El mismo sábado a las 04:30 UTC: en Lima todavía es viernes 23:30. */
    private static final Instant VIERNES_2330_LIMA = Instant.parse("2026-09-26T04:30:00Z");
    private static final LocalDate VIERNES = LocalDate.of(2026, 9, 25);
    private static final Instant DESDE_SIEMPRE = Instant.parse("2026-08-01T05:00:00Z");

    private static final UUID FENIX = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID AMANECER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UserId MENTORA = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UserId MENTOR_AMANECER = UserId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a2"));

    private BancoDeMentoria banco;
    private final Map<UserId, VentanaDelSemaforo> semanas = new HashMap<>();
    private final Set<UserId> conBaseCaida = new HashSet<>();
    private final List<List<UserId>> consultas = new ArrayList<>();
    private final List<Object> publicados = new ArrayList<>();
    private final List<Boolean> publicadosEnTransaccion = new ArrayList<>();
    private TransaccionesDePrueba transacciones;

    @BeforeEach
    void preparar() {
        banco = new BancoDeMentoria();
        transacciones = new TransaccionesDePrueba();
    }

    private ResumenSemanalDelSemaforoService servicio(Instant ahora) {
        return new ResumenSemanalDelSemaforoService(banco.acompanamiento, semaforo, evento -> {
            publicados.add(evento);
            publicadosEnTransaccion.add(transacciones.abierta);
        }, transacciones, FixedClock.at(ahora));
    }

    // ── casos ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("al abrir la ventana publica el resumen del grupo con el conteo de las semanas cerradas")
    void publicaElResumenDelGrupo() {
        banco.grupo(FENIX, "Grupo Fenix", MENTORA, UUID.randomUUID(), 3);
        aprendiz(FENIX, cerrada(ColorSemaforo.VERDE));
        aprendiz(FENIX, cerrada(ColorSemaforo.AMARILLO));
        aprendiz(FENIX, cerrada(ColorSemaforo.ROJO));

        assertThat(servicio(SABADO_0040_LIMA).resumir()).isEqualTo(1);

        ResumenSemanalDelGrupoEvent resumen = unicoDelGrupo();
        assertThat(resumen.claveDeduplicacion()).isEqualTo(ReglasDelResumenSemanal.claveDelGrupo(FENIX, VIERNES));
        assertThat(resumen.mentorId()).isEqualTo(MENTORA.value());
        assertThat(resumen.grupoId()).isEqualTo(FENIX);
        assertThat(resumen.grupoNombre()).isEqualTo("Grupo Fenix");
        assertThat(resumen.desde()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(resumen.hasta()).isEqualTo(VIERNES);
        assertThat(resumen.generadoEn()).isEqualTo(SABADO_0040_LIMA);
        assertThat(resumen.rutaApp()).isEqualTo("/mentor/groups/" + FENIX + "/semaforo");
        assertThat(List.of(resumen.verde(), resumen.amarillo(), resumen.rojo(), resumen.sinDatos(), resumen.total()))
                .containsExactly(1, 1, 1, 0, 3);
    }

    @Test
    @DisplayName("un aprendiz sin semana cerrada o sin semaforo cuenta como sin datos, no con su color vigente")
    void losQueNoCerraronCuentanComoSinDatos() {
        banco.grupo(FENIX, "Grupo Fenix", MENTORA, UUID.randomUUID(), 3);
        aprendiz(FENIX, cerrada(ColorSemaforo.VERDE));
        aprendiz(FENIX, abierta(ColorSemaforo.VERDE));
        aprendiz(FENIX, null);

        servicio(SABADO_0040_LIMA).resumir();

        ResumenSemanalDelGrupoEvent resumen = unicoDelGrupo();
        assertThat(List.of(resumen.verde(), resumen.amarillo(), resumen.rojo(), resumen.sinDatos(), resumen.total()))
                .containsExactly(1, 0, 0, 2, 3);
    }

    @Test
    @DisplayName("el mentor que ademas cursa no entra en el semaforo de su grupo, y la consulta es UNA por grupo")
    void elMentorQuedaAfueraYLaConsultaEsEnLote() {
        banco.grupo(FENIX, "Grupo Fenix", MENTORA, UUID.randomUUID(), 3);
        UserId ana = aprendiz(FENIX, cerrada(ColorSemaforo.VERDE));
        UserId beto = aprendiz(FENIX, cerrada(ColorSemaforo.ROJO));
        banco.alumno(FENIX, MENTORA, "La Mentora", DESDE_SIEMPRE, null);
        semanas.put(MENTORA, cerrada(ColorSemaforo.ROJO));

        servicio(SABADO_0040_LIMA).resumir();

        assertThat(consultas).containsExactly(List.of(ana, beto));
        assertThat(unicoDelGrupo().total()).isEqualTo(2);
    }

    @Test
    @DisplayName("a las 04:30 UTC del sabado en Lima todavia es viernes: no consulta ni publica nada")
    void antesDelCierreLocalNoHaceNada() {
        banco.grupo(FENIX, "Grupo Fenix", MENTORA, UUID.randomUUID(), 3);
        aprendiz(FENIX, cerrada(ColorSemaforo.VERDE));

        assertThat(servicio(VIERNES_2330_LIMA).resumir()).isZero();

        assertThat(consultas).isEmpty();
        assertThat(publicados).isEmpty();
    }

    @Test
    @DisplayName("si el cierre de points todavia no paso por el grupo, espera a la corrida siguiente")
    void sinNingunaSemanaCerradaEspera() {
        banco.grupo(FENIX, "Grupo Fenix", MENTORA, UUID.randomUUID(), 3);
        UserId ana = aprendiz(FENIX, abierta(ColorSemaforo.VERDE));

        assertThat(servicio(SABADO_0040_LIMA).resumir()).isZero();
        assertThat(publicados).isEmpty();

        semanas.put(ana, cerrada(ColorSemaforo.VERDE));
        assertThat(servicio(SABADO_0040_LIMA.plusSeconds(3600)).resumir()).isEqualTo(1);
        assertThat(unicoDelGrupo().verde()).isEqualTo(1);
    }

    @Test
    @DisplayName("el resumen general suma los grupos de la corrida y usa la clave de la semana")
    void elGeneralSumaLosGrupos() {
        banco.grupo(FENIX, "Grupo Fenix", MENTORA, UUID.randomUUID(), 3);
        aprendiz(FENIX, cerrada(ColorSemaforo.VERDE));
        aprendiz(FENIX, cerrada(ColorSemaforo.ROJO));
        banco.grupo(AMANECER, "Grupo Amanecer", MENTOR_AMANECER, UUID.randomUUID(), 3);
        aprendiz(AMANECER, cerrada(ColorSemaforo.AMARILLO));
        aprendiz(AMANECER, abierta(ColorSemaforo.VERDE));

        assertThat(servicio(SABADO_0040_LIMA).resumir()).isEqualTo(2);

        ResumenSemanalGeneralEvent general = unicoGeneral();
        assertThat(general.claveDeduplicacion()).isEqualTo(ReglasDelResumenSemanal.claveGeneral(VIERNES));
        assertThat(general.desde()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(general.hasta()).isEqualTo(VIERNES);
        assertThat(general.generadoEn()).isEqualTo(SABADO_0040_LIMA);
        assertThat(general.rutaApp()).isEqualTo("/semaforo/grupos");
        assertThat(List.of(general.grupos(), general.verde(), general.amarillo(), general.rojo(), general.sinDatos(),
                general.total())).containsExactly(2, 1, 1, 1, 1, 4);
    }

    @Test
    @DisplayName("un grupo que falla no frena a los demas ni entra en el resumen general")
    void unGrupoQueFallaNoFrenaALosDemas() {
        banco.grupo(FENIX, "Grupo Fenix", MENTORA, UUID.randomUUID(), 3);
        conBaseCaida.add(aprendiz(FENIX, cerrada(ColorSemaforo.VERDE)));
        banco.grupo(AMANECER, "Grupo Amanecer", MENTOR_AMANECER, UUID.randomUUID(), 3);
        aprendiz(AMANECER, cerrada(ColorSemaforo.AMARILLO));

        assertThat(servicio(SABADO_0040_LIMA).resumir()).isEqualTo(1);

        assertThat(unicoDelGrupo().grupoId()).isEqualTo(AMANECER);
        assertThat(unicoGeneral().grupos()).isEqualTo(1);
        assertThat(unicoGeneral().amarillo()).isEqualTo(1);
    }

    @Test
    @DisplayName("un grupo de otra zona espera su propia ventana")
    void cadaGrupoEnSuZona() {
        banco.grupo(FENIX, "Grupo Fenix", MENTORA, UUID.randomUUID(), 3);
        aprendiz(FENIX, cerrada(ColorSemaforo.VERDE));
        // Sábado 05:40 UTC es 07:40 en Madrid: su ventana ya pasó.
        banco.grupos.add(new GrupoAcompanado(AMANECER, "Grupo Amanecer", MENTOR_AMANECER, UUID.randomUUID(),
                "Europe/Madrid", 3));
        aprendiz(AMANECER, cerrada(ColorSemaforo.ROJO));

        servicio(SABADO_0040_LIMA).resumir();

        assertThat(unicoDelGrupo().grupoId()).isEqualTo(FENIX);
        assertThat(unicoGeneral().grupos()).isEqualTo(1);
    }

    @Test
    @DisplayName("cada evento sale en su propia transaccion real, y la transaccion se confirma")
    void cadaEventoEnSuPropiaTransaccion() {
        banco.grupo(FENIX, "Grupo Fenix", MENTORA, UUID.randomUUID(), 3);
        aprendiz(FENIX, cerrada(ColorSemaforo.VERDE));

        servicio(SABADO_0040_LIMA).resumir();

        assertThat(publicados).hasSize(2);
        assertThat(publicadosEnTransaccion).containsExactly(true, true);
        assertThat(transacciones.confirmadas).isEqualTo(2);
        assertThat(transacciones.propagaciones).containsOnly(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    @DisplayName("repetir la corrida en la ventana publica las MISMAS claves: la deduplicacion es de notifications")
    void corridaRepetidaMismasClaves() {
        banco.grupo(FENIX, "Grupo Fenix", MENTORA, UUID.randomUUID(), 3);
        aprendiz(FENIX, cerrada(ColorSemaforo.VERDE));

        servicio(SABADO_0040_LIMA).resumir();
        servicio(SABADO_0040_LIMA.plusSeconds(3600)).resumir();

        List<UUID> claves = publicados.stream().map(ResumenSemanalDelSemaforoServiceTest::claveDe).toList();
        assertThat(claves).hasSize(4);
        assertThat(claves.get(2)).isEqualTo(claves.get(0));
        assertThat(claves.get(3)).isEqualTo(claves.get(1));
    }

    // ── armado ─────────────────────────────────────────────────────────────

    private UserId aprendiz(UUID grupo, VentanaDelSemaforo semana) {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        banco.alumno(grupo, aprendiz, "Aprendiz", DESDE_SIEMPRE, null);
        if (semana != null) {
            semanas.put(aprendiz, semana);
        }
        return aprendiz;
    }

    private ResumenSemanalDelGrupoEvent unicoDelGrupo() {
        List<ResumenSemanalDelGrupoEvent> delGrupo = publicados.stream()
                .filter(ResumenSemanalDelGrupoEvent.class::isInstance).map(ResumenSemanalDelGrupoEvent.class::cast)
                .toList();
        assertThat(delGrupo).hasSize(1);
        return delGrupo.getFirst();
    }

    private ResumenSemanalGeneralEvent unicoGeneral() {
        List<ResumenSemanalGeneralEvent> generales = publicados.stream()
                .filter(ResumenSemanalGeneralEvent.class::isInstance).map(ResumenSemanalGeneralEvent.class::cast)
                .toList();
        assertThat(generales).hasSize(1);
        return generales.getFirst();
    }

    private static UUID claveDe(Object evento) {
        return evento instanceof ResumenSemanalDelGrupoEvent grupo
                ? grupo.claveDeduplicacion()
                : ((ResumenSemanalGeneralEvent) evento).claveDeduplicacion();
    }

    private static VentanaDelSemaforo cerrada(ColorSemaforo color) {
        return semana(color, true);
    }

    private static VentanaDelSemaforo abierta(ColorSemaforo color) {
        return semana(color, false);
    }

    /** Coherente: sin datos no tiene porcentaje ni días medidos; los demás, un porcentaje de su color. */
    private static VentanaDelSemaforo semana(ColorSemaforo color, boolean cerrada) {
        BigDecimal porcentaje = switch (color) {
            case VERDE -> new BigDecimal("88.0");
            case AMARILLO -> new BigDecimal("65.4");
            case ROJO -> new BigDecimal("30.0");
            case SIN_DATOS -> null;
        };
        return new VentanaDelSemaforo(VIERNES.minusDays(6), VIERNES, porcentaje, color, porcentaje == null ? 0 : 5,
                cerrada, List.of());
    }

    /** Doble del semáforo de points: lee de {@link #semanas} y anota cada consulta. */
    private final SemaforoFinder semaforo = new SemaforoFinder() {
        @Override
        public Map<UserId, VentanaDelSemaforo> vigenteDe(Collection<UserId> participantes) {
            throw new UnsupportedOperationException("el resumen del sabado no mira la ventana vigente");
        }

        @Override
        public Map<UserId, VentanaDelSemaforo> semanaDe(Collection<UserId> participantes, LocalDate semanaHasta) {
            assertThat(semanaHasta).isEqualTo(VIERNES);
            consultas.add(List.copyOf(participantes));
            if (participantes.stream().anyMatch(conBaseCaida::contains)) {
                throw new IllegalStateException("se cayo la conexion");
            }
            Map<UserId, VentanaDelSemaforo> encontradas = new HashMap<>();
            participantes.stream().filter(semanas::containsKey).forEach(p -> encontradas.put(p, semanas.get(p)));
            return encontradas;
        }

        @Override
        public DetalleDelSemaforo detalleDe(UserId participante, int cuantas) {
            throw new UnsupportedOperationException("el resumen del sabado no mira el detalle");
        }
    };

    /** Anota si hay una transacción abierta, cuántas se confirmaron y con qué propagación se pidieron. */
    private static final class TransaccionesDePrueba implements PlatformTransactionManager {
        private boolean abierta;
        private int confirmadas;
        private final List<Integer> propagaciones = new ArrayList<>();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definicion) {
            propagaciones.add(definicion.getPropagationBehavior());
            abierta = true;
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus estado) {
            abierta = false;
            confirmadas++;
        }

        @Override
        public void rollback(TransactionStatus estado) {
            abierta = false;
        }
    }
}
