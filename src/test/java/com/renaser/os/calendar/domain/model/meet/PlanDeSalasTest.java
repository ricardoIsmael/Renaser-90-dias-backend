package com.renaser.os.calendar.domain.model.meet;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.meet.PlanDeSalas.Accion;
import com.renaser.os.calendar.domain.model.meet.PlanDeSalas.OcurrenciaDeseada;
import com.renaser.os.calendar.domain.model.meet.PlanDeSalas.Paso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanDeSalasTest {

    private static final Instant AHORA = Instant.parse("2026-11-05T15:00:00Z");
    private static final LocalDate HOY = LocalDate.of(2026, 11, 5);
    private static final EventoId EVENTO = EventoId.of(UUID.randomUUID());

    private static Map<LocalDate, OcurrenciaDeseada> fechas(LocalDate... dias) {
        Map<LocalDate, OcurrenciaDeseada> deseadas = new TreeMap<>();
        for (LocalDate dia : dias) {
            Instant inicio = dia.atTime(19, 0).toInstant(java.time.ZoneOffset.ofHours(-5));
            deseadas.put(dia, new OcurrenciaDeseada(dia, inicio, inicio.plusSeconds(3600), "Clase"));
        }
        return deseadas;
    }

    private static SalaMeet creada(LocalDate dia) {
        SalaMeet sala = SalaMeet.nueva(UUID.randomUUID(), EVENTO, dia, "id" + dia.toString().replace("-", ""), AccesoSalaMeet.ABIERTA, AHORA);
        sala.completarCon("https://meet.google.com/abc-defg-hij", AHORA);
        sala.registrarAccesoConfirmado(AccesoSalaMeet.ABIERTA, AHORA);
        return sala;
    }

    private static List<Accion> acciones(List<Paso> pasos) {
        return pasos.stream().map(Paso::accion).toList();
    }

    // spec 040 FR-017, SC-012
    @Test
    @DisplayName("FR-017: cada fecha que se quiere y no tiene sala se crea, en orden de fecha")
    void creaLasFechasQueFaltan() {
        List<Paso> plan = PlanDeSalas.calcular(true, HOY, fechas(LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 24)),
                List.of(), AHORA, false);

        assertThat(acciones(plan)).containsExactly(Accion.CREAR, Accion.CREAR, Accion.CREAR);
        assertThat(plan).extracting(Paso::fecha).containsExactly(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 24));
    }

    // spec 040 FR-017
    @Test
    @DisplayName("FR-017: lo que ya está creado y no cambió no genera ningún paso")
    void loQueEstaBienNoSeToca() {
        List<Paso> plan = PlanDeSalas.calcular(true, HOY, fechas(LocalDate.of(2026, 11, 10)), List.of(creada(LocalDate.of(2026, 11, 10))), AHORA, false);

        assertThat(plan).isEmpty();
    }

    // spec 040 FR-017, FR-005
    @Test
    @DisplayName("FR-017: una fecha que ya no existe (se canceló o se corrió la serie) se borra; las demás se conservan")
    void borraLaQueYaNoSeQuiere() {
        SalaMeet cancelada = creada(LocalDate.of(2026, 11, 17));
        List<SalaMeet> existentes = List.of(creada(LocalDate.of(2026, 11, 10)), cancelada, creada(LocalDate.of(2026, 11, 24)));

        List<Paso> plan = PlanDeSalas.calcular(true, HOY, fechas(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 24)), existentes, AHORA, false);

        assertThat(plan).singleElement().satisfies(paso -> {
            assertThat(paso.accion()).isEqualTo(Accion.BORRAR);
            assertThat(paso.sala()).isSameAs(cancelada);
        });
    }

    // spec 040 FR-017
    @Test
    @DisplayName("FR-017: una fecha que ya pasó no se borra ni se crea mientras el evento siga vivo")
    void lasFechasPasadasNoSeTocan() {
        SalaMeet pasada = creada(LocalDate.of(2026, 11, 3));

        List<Paso> plan = PlanDeSalas.calcular(true, HOY, fechas(LocalDate.of(2026, 11, 10)), List.of(pasada), AHORA, false);

        assertThat(acciones(plan)).containsExactly(Accion.CREAR);
        assertThat(plan).extracting(Paso::fecha).doesNotContain(LocalDate.of(2026, 11, 3));
        assertThat(PlanDeSalas.calcular(true, HOY, fechas(LocalDate.of(2026, 11, 2)), List.of(), AHORA, false)).as("sin sala nueva para el pasado").isEmpty();
    }

    // spec 040 FR-005
    @Test
    @DisplayName("FR-005: si el evento ya no existe o dejó de pedir Meet, se borran todas las salas, también las ya pasadas")
    void sinEventoSeBorraTodo() {
        List<SalaMeet> existentes = new ArrayList<>(List.of(creada(LocalDate.of(2026, 11, 3)), creada(LocalDate.of(2026, 11, 10))));

        List<Paso> plan = PlanDeSalas.calcular(false, HOY, Map.of(), existentes, AHORA, false);

        assertThat(acciones(plan)).containsExactly(Accion.BORRAR, Accion.BORRAR);
    }

    // spec 040 FR-005
    @Test
    @DisplayName("FR-005: una sala marcada para sincronizar se actualiza; el enlace no cambia")
    void actualizaLoPendienteDeSincronizar() {
        SalaMeet sala = creada(LocalDate.of(2026, 11, 10));
        sala.pedirSincronizar(AHORA);

        List<Paso> plan = PlanDeSalas.calcular(true, HOY, fechas(LocalDate.of(2026, 11, 10)), List.of(sala), AHORA, false);

        assertThat(acciones(plan)).containsExactly(Accion.ACTUALIZAR);
    }

    // spec 040 FR-008
    @Test
    @DisplayName("FR-008: lo que falló espera su turno; con «Reintentar» a mano (forzar) no espera")
    void respetaLaEsperaSalvoQueSeFuerce() {
        SalaMeet fallida = SalaMeet.nueva(UUID.randomUUID(), EVENTO, LocalDate.of(2026, 11, 10), "id1", AccesoSalaMeet.ABIERTA, AHORA);
        fallida.fallarAlCrear("x", AHORA, AHORA.plusSeconds(600));

        assertThat(PlanDeSalas.calcular(true, HOY, fechas(LocalDate.of(2026, 11, 10)), List.of(fallida), AHORA, false)).isEmpty();
        assertThat(acciones(PlanDeSalas.calcular(true, HOY, fechas(LocalDate.of(2026, 11, 10)), List.of(fallida), AHORA.plusSeconds(601), false)))
                .containsExactly(Accion.CREAR);
        assertThat(acciones(PlanDeSalas.calcular(true, HOY, fechas(LocalDate.of(2026, 11, 10)), List.of(fallida), AHORA, true)))
                .containsExactly(Accion.CREAR);
    }

    // spec 040 FR-023
    @Test
    @DisplayName("FR-023: una sala creada a la que le falta dejar el acceso como se eligió pide ajustarlo")
    void ajustaElAccesoPendiente() {
        SalaMeet sala = creada(LocalDate.of(2026, 11, 10));
        sala.elegirAcceso(AccesoSalaMeet.CON_PERMISO, AHORA);

        assertThat(acciones(PlanDeSalas.calcular(true, HOY, fechas(LocalDate.of(2026, 11, 10)), List.of(sala), AHORA, false)))
                .containsExactly(Accion.AJUSTAR_ACCESO);
    }

    // spec 040 FR-012, FR-017
    @Test
    @DisplayName("FR-017: una fecha que vuelve después de haberse borrado reutiliza su fila (la lápida) en vez de crear otra")
    void laLapidaSeReactiva() {
        SalaMeet lapida = creada(LocalDate.of(2026, 11, 10));
        lapida.marcarBorrada(AHORA);

        List<Paso> plan = PlanDeSalas.calcular(true, HOY, fechas(LocalDate.of(2026, 11, 10)), List.of(lapida), AHORA, false);

        assertThat(plan).singleElement().satisfies(paso -> {
            assertThat(paso.accion()).isEqualTo(Accion.CREAR);
            assertThat(paso.sala()).isSameAs(lapida);
        });
        assertThat(PlanDeSalas.calcular(false, HOY, Map.of(), List.of(lapida), AHORA, true)).as("una lápida no se vuelve a borrar").isEmpty();
    }
}
