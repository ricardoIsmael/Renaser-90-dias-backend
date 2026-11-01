package com.renaser.os.rag;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.plataforma.application.services.CopiaDeEstados;
import com.renaser.os.rag.api.Analisis8020Finder;
import com.renaser.os.rag.api.Analisis8020Finder.Tipo;
import com.renaser.os.rag.api.Analisis8020Finder.VistaDelAnalisis;
import com.renaser.os.rag.application.ports.in.analisis.GenerarAnalisisPendientesUseCase;
import com.renaser.os.rag.application.ports.out.analisis.GenerarAnalisis8020Port;
import com.renaser.os.rag.application.ports.out.analisis.GenerarAnalisis8020Port.PedidoDeAnalisis;
import com.renaser.os.rag.application.services.analisis.JsonDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TextoDelAnalisis;
import com.renaser.os.shared.domain.ProveedorIaNoDisponibleException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El análisis 80/20 de punta a punta contra Postgres y Redis reales (spec 031, D-306): el barrido horario de verdad, las lecturas de
 * {@code rocks} y {@code habits} de verdad, el guardado con su {@code CHECK}, el aviso por el outbox y el borrado con la cuenta. Lo único
 * que se dobla es el proveedor de IA, y se cuenta cuántas veces se le llama: es el costo.
 *
 * <p>Ana empezó el lunes 17 de agosto (Lima). A las 00:30 del lunes 19 de octubre, hora de Lima (05:30 UTC), su semana 9 (12 al 18) acaba de
 * cerrar; el mes 2 (14 de septiembre al 11 de octubre) cerró hace ocho días, fuera de la ventana de puesta al día.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class Analisis8020IT {

    private static final Instant LUNES_00_30_EN_LIMA = Instant.parse("2026-10-19T05:30:00Z");
    private static final Instant DOMINGO_23_30_EN_LIMA = Instant.parse("2026-10-19T04:30:00Z");
    private static final LocalDate SEMANA_9 = LocalDate.of(2026, 10, 12);

    @MockitoBean
    private GenerarAnalisis8020Port ia;
    @Autowired
    private GenerarAnalisisPendientesUseCase barrido;
    @Autowired
    private Analisis8020Finder lectura;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private CopiaDeEstados copiaDeEstados;

    private UUID ana;

    @BeforeEach
    void sembrar() {
        reloj.fijar(LUNES_00_30_EN_LIMA);
        ana = persona("Ana Pérez", false);
        sembrarSemana9(ana, 9);
        when(ia.disponible()).thenReturn(true);
        doAnswer(pedido -> textoQueCita(pedido.getArgument(0))).when(ia).generar(any());
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave = 'ia.analisis'");
        copiaDeEstados.recargar();
        jdbc.update("DELETE FROM renaser.notificaciones WHERE tipo = 'ANALISIS_80_20'::renaser.tipo_notificacion");
        jdbc.update("DELETE FROM renaser.usuarios WHERE email LIKE '%@analisis.test' OR email LIKE 'ver-como+%@renaser.invalid'");
    }

    /** Como lo haría una IA obediente: cita las cifras que le dieron, ni una más. */
    private static TextoDelAnalisis textoQueCita(PedidoDeAnalisis pedido) {
        JsonNode cifras = JsonDelAnalisis.arbol(pedido.datosEnJson()).path("cifras");
        return new TextoDelAnalisis("Cumpliste " + cifras.path("cumplidas").asInt() + " de " + cifras.path("planificadas").asInt()
                + " acciones.", "Una llamada importante quedó pendiente.", "Tu energía fue estable.", List.of("Haz primero la llamada."));
    }

    private UUID persona(String nombre, boolean deCuentaDePrueba) {
        UUID id = UUID.randomUUID();
        // Una cuenta de prueba de «ver como» (V117) lleva un correo `ver-como+…@renaser.invalid` y no tiene contraseña.
        String correo = deCuentaDePrueba ? "ver-como+" + id + "@renaser.invalid" : id + "@analisis.test";
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, es_cuenta_de_prueba) "
                + "VALUES (?, ?, ?, CAST('APRENDIZ' AS renaser.rol_usuario), ?)", id, correo, nombre, deCuentaDePrueba);
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa, fecha_inicio, programa_activado_en, timezone) "
                + "VALUES (?, 56, DATE '2026-08-17', TIMESTAMPTZ '2026-08-16 12:00:00+00', 'America/Lima')", id);
        return id;
    }

    /** Hasta nueve acciones de la semana 9 en cuatro días, con su impacto; las tres últimas pendientes; y cuatro respuestas del Código Renaser. */
    private void sembrarSemana9(UUID persona, int cuantas) {
        Object[][] acciones = {
                {"2026-10-12", "CUERPO", 1, 9, true}, {"2026-10-12", "CUERPO", 2, 5, true}, {"2026-10-12", "TRABAJO", 1, 8, true},
                {"2026-10-13", "TRABAJO", 2, 4, true}, {"2026-10-13", "RELACIONES", 1, 7, false}, {"2026-10-14", "CUERPO", 1, 9, true},
                {"2026-10-14", "TRABAJO", 1, 10, false}, {"2026-10-15", "RELACIONES", 3, 1, true}, {"2026-10-15", "TRABAJO", 3, 6, true}};
        for (int i = 0; i < cuantas; i++) {
            Object[] a = acciones[i];
            jdbc.update("INSERT INTO renaser.rocas_diarias (id, participante_id, fecha, posicion, titulo, color, puntaje_impacto, eje, "
                            + "completada, completada_en) VALUES (?, ?, CAST(? AS date), ?, ?, CAST(? AS renaser.color_pareto), ?, "
                            + "CAST(? AS renaser.eje_objetivo), ?, ?)",
                    UUID.randomUUID(), persona, a[0], a[2], "Acción " + i, (int) a[2] == 1 ? "VERDE" : (int) a[2] == 2 ? "AMARILLA" : "ROJA",
                    a[3], a[1], a[4], (boolean) a[4] ? java.sql.Timestamp.from(Instant.parse(a[0] + "T18:00:00Z")) : null);
        }
        for (int i = 0; i < 4; i++) {
            jdbc.update("INSERT INTO renaser.registros_radar (id, participante_id, que_hago, que_pienso, que_siento, nivel_energia, que_evito, "
                            + "creado_en) VALUES (?, ?, 'Trabajando', 'En lo mío', 'Con calma', ?, 'El celular', ?)",
                    UUID.randomUUID(), persona, 4 + i, java.sql.Timestamp.from(Instant.parse("2026-10-13T14:00:00Z").plusSeconds(i * 3600L)));
        }
    }

    private List<Map<String, Object>> filas(UUID persona) {
        return jdbc.queryForList("SELECT estado, intentos, formacion, inicio::text AS inicio, lo_que_mas_movio, cifras::text AS cifras FROM renaser.analisis_80_20 "
                + "WHERE participante_id = ? ORDER BY periodo, inicio", persona);
    }

    @Test
    @DisplayName("al cerrar su semana el barrido genera el análisis UNA vez, lo guarda con las cifras que cita, avisa, y abrirlo no vuelve a pagar la IA")
    void generaUnaVez() {
        barrido.generarLosQueTocan();
        barrido.generarLosQueTocan();

        assertThat(filas(ana)).singleElement().satisfies(fila -> {
            assertThat(fila.get("estado")).isEqualTo("LISTO");
            assertThat(fila.get("formacion")).isEqualTo("ESCUELA");
            assertThat(fila.get("lo_que_mas_movio")).isEqualTo("Cumpliste 7 de 9 acciones.");
            assertThat((String) fila.get("cifras")).contains("\"planificadas\": 9");
        });
        verify(ia, times(1)).generar(any());

        for (int abierta = 0; abierta < 20; abierta++) {
            VistaDelAnalisis vista = lectura.de(UserId.of(ana), Tipo.SEMANA, SEMANA_9);
            assertThat(vista.estado()).isEqualTo("LISTO");
            assertThat(vista.analisis().loQueMasMovio()).isEqualTo("Cumpliste 7 de 9 acciones.");
            assertThat(vista.cifras().planificadas()).isEqualTo(9);
            assertThat(vista.cifras().cumplidas()).isEqualTo(7);
            assertThat(vista.cifras().verdes().planificadas()).isEqualTo(5); // las cinco de posición 1
            assertThat(vista.codigoRenaser().respuestas()).isEqualTo(4);
        }
        verify(ia, times(1)).generar(any());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM renaser.notificaciones WHERE usuario_id = ? AND tipo = 'ANALISIS_80_20'::renaser.tipo_notificacion",
                Integer.class, ana)).isEqualTo(1));
    }

    @Test
    @DisplayName("la semana en curso solo muestra cifras al día: ni análisis ni llamada a la IA")
    void semanaEnCurso() {
        VistaDelAnalisis vista = lectura.de(UserId.of(ana), Tipo.SEMANA, null);

        assertThat(vista.estado()).isEqualTo("EN_CURSO");
        assertThat(vista.numero()).isEqualTo(10);
        assertThat(vista.anterior()).isEqualTo(SEMANA_9);
        assertThat(vista.cifras().planificadas()).isZero();
        verify(ia, never()).generar(any());
    }

    @Test
    @DisplayName("SC-008: una semana con menos del mínimo no llama a la IA y la hoja lo dice; una cuenta de prueba de «ver como» tampoco genera nada")
    void sinDatosYCuentaDePrueba() {
        UUID luis = persona("Luis Poco", false);
        sembrarSemana9(luis, 2);
        UUID cuentaDePrueba = persona("Prueba", true);
        sembrarSemana9(cuentaDePrueba, 9);

        barrido.generarLosQueTocan();

        assertThat(filas(luis)).singleElement().extracting(fila -> fila.get("estado")).isEqualTo("SIN_DATOS");
        assertThat(filas(cuentaDePrueba)).isEmpty();
        verify(ia, times(1)).generar(any());
        VistaDelAnalisis vista = lectura.de(UserId.of(luis), Tipo.SEMANA, SEMANA_9);
        assertThat(vista.estado()).isEqualTo("SIN_DATOS");
        assertThat(vista.falta()).containsExactly("ACCIONES", "DIAS");
        assertThat(vista.analisis()).isNull();
    }

    @Test
    @DisplayName("FR-016: con la IA caída queda pendiente y se reintenta pasada la espera, una sola vez cuando responde")
    void iaCaida() {
        doThrow(new ProveedorIaNoDisponibleException("caída", Duration.ofSeconds(10))).when(ia).generar(any());

        barrido.generarLosQueTocan();
        assertThat(filas(ana)).singleElement().satisfies(fila -> {
            assertThat(fila.get("estado")).isEqualTo("PENDIENTE");
            assertThat(fila.get("intentos")).isEqualTo(1);
            assertThat(fila.get("lo_que_mas_movio")).isNull();
        });
        assertThat(lectura.de(UserId.of(ana), Tipo.SEMANA, SEMANA_9).estado()).isEqualTo("PENDIENTE");

        barrido.generarLosQueTocan();
        verify(ia, times(1)).generar(any());

        doAnswer(pedido -> textoQueCita(pedido.getArgument(0))).when(ia).generar(any());
        reloj.avanzar(Duration.ofHours(7));
        barrido.generarLosQueTocan();
        barrido.generarLosQueTocan();

        assertThat(filas(ana)).singleElement().satisfies(fila -> {
            assertThat(fila.get("estado")).isEqualTo("LISTO");
            assertThat(fila.get("intentos")).isEqualTo(2);
        });
        verify(ia, times(2)).generar(any());
    }

    @Test
    @DisplayName("FR-016: tres fallos y el periodo queda sin análisis, con sus cifras; el sistema deja de intentar")
    void seAcabanLosIntentos() {
        doThrow(new ProveedorIaNoDisponibleException("caída", Duration.ofSeconds(10))).when(ia).generar(any());

        for (int intento = 0; intento < 5; intento++) {
            barrido.generarLosQueTocan();
            reloj.avanzar(Duration.ofHours(7));
        }

        assertThat(filas(ana)).singleElement().extracting(fila -> fila.get("estado")).isEqualTo("AGOTADO");
        verify(ia, times(3)).generar(any());
        VistaDelAnalisis vista = lectura.de(UserId.of(ana), Tipo.SEMANA, SEMANA_9);
        assertThat(vista.estado()).isEqualTo("SIN_ANALISIS");
        assertThat(vista.cifras().planificadas()).isEqualTo(9);
    }

    @Test
    @DisplayName("FR-009: un texto con una cifra que no es la calculada no se guarda: la fila queda pendiente y sin texto")
    void cifraAjena() {
        doReturn(new TextoDelAnalisis("Cumpliste 8 de 9 acciones.", "Nada.", "Bien.", List.of("Sigue."))).when(ia).generar(any());

        barrido.generarLosQueTocan();

        assertThat(filas(ana)).singleElement().satisfies(fila -> {
            assertThat(fila.get("estado")).isEqualTo("PENDIENTE");
            assertThat(fila.get("lo_que_mas_movio")).isNull();
        });
    }

    @Test
    @DisplayName("FR-019: con ia.analisis apagada por Soporte TI el barrido no corre y las cifras se siguen viendo; al prenderla se genera lo pendiente, sin duplicar")
    void conLaFuncionalidadApagada() {
        jdbc.update("INSERT INTO renaser.funcionalidades_estado (clave, prendida_global, version, actualizado_por, actualizado_en) "
                + "VALUES ('ia.analisis', false, nextval('renaser.funcionalidades_version_seq'), ?, now())", ana);
        copiaDeEstados.recargar();

        barrido.generarLosQueTocan();

        assertThat(filas(ana)).isEmpty();
        verify(ia, never()).generar(any());
        assertThat(lectura.de(UserId.of(ana), Tipo.SEMANA, SEMANA_9).cifras().planificadas()).isEqualTo(9);

        jdbc.update("DELETE FROM renaser.funcionalidades_estado WHERE clave = 'ia.analisis'");
        copiaDeEstados.recargar();
        reloj.avanzar(Duration.ofDays(2));
        barrido.generarLosQueTocan();
        barrido.generarLosQueTocan();

        assertThat(filas(ana)).singleElement().extracting(fila -> fila.get("estado")).isEqualTo("LISTO");
        verify(ia, times(1)).generar(any());
    }

    @Test
    @DisplayName("FR-018: con el reloj en la madrugada UTC, el domingo 23:30 de Lima la semana todavía no cerró y no se genera nada")
    void madrugadaUtc() {
        reloj.fijar(DOMINGO_23_30_EN_LIMA);

        barrido.generarLosQueTocan();

        // La semana 9 (12 al 18 de oct) todavía no cerró en Lima: ni fila ni IA. Lo que sí cerró antes (la semana 8, sin acciones) queda
        // anotado como «sin datos», sin llamar a la IA.
        assertThat(filas(ana)).noneMatch(fila -> "2026-10-12".equals(fila.get("inicio")));
        verify(ia, never()).generar(any());
    }

    @Test
    @DisplayName("sin proveedor de IA configurado (desarrollo) no se genera ni se cuentan intentos, y la hoja sigue con sus cifras")
    void sinProveedor() {
        when(ia.disponible()).thenReturn(false);

        barrido.generarLosQueTocan();

        assertThat(filas(ana)).isEmpty();
        assertThat(lectura.de(UserId.of(ana), Tipo.SEMANA, SEMANA_9).estado()).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("FR-022: lo que quedó de otra formación no se muestra, y un traslado no arrastra el análisis a la formación nueva")
    void soloLaFormacionDeHoy() {
        barrido.generarLosQueTocan();
        assertThat(lectura.de(UserId.of(ana), Tipo.SEMANA, SEMANA_9).estado()).isEqualTo("LISTO");

        jdbc.update("UPDATE renaser.participantes_programa SET formacion = 'DIPLOMADO' WHERE usuario_id = ?", ana);

        VistaDelAnalisis vista = lectura.de(UserId.of(ana), Tipo.SEMANA, SEMANA_9);
        assertThat(vista.estado()).isEqualTo("PENDIENTE");
        assertThat(vista.analisis()).isNull();
        assertThat(lectura.ultimoListo(UserId.of(ana), Tipo.SEMANA)).isEmpty();
    }

    @Test
    @DisplayName("FR-025: eliminar la cuenta borra los análisis de la persona")
    void seBorraConLaCuenta() {
        barrido.generarLosQueTocan();
        assertThat(filas(ana)).hasSize(1);

        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", ana);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.analisis_80_20 WHERE participante_id = ?", Integer.class, ana)).isZero();
    }

    @Test
    @DisplayName("la base no deja guardar un texto en un análisis que no está listo, ni dos análisis del mismo periodo")
    void restriccionesDeLaBase() {
        barrido.generarLosQueTocan();
        UUID id = jdbc.queryForObject("SELECT id FROM renaser.analisis_80_20 WHERE participante_id = ?", UUID.class, ana);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("UPDATE renaser.analisis_80_20 SET estado = 'PENDIENTE' WHERE id = ?", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("INSERT INTO renaser.analisis_80_20 (id, participante_id, formacion, "
                        + "periodo, numero, inicio, fin, estado, creado_en) VALUES (?, ?, 'ESCUELA', 'SEMANA', 9, DATE '2026-10-12', "
                        + "DATE '2026-10-18', 'PENDIENTE', now())", UUID.randomUUID(), ana))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }
}
