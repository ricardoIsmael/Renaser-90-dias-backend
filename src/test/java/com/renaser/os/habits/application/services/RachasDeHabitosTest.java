package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaUseCase.RegistrosDelDia;
import com.renaser.os.habits.application.ports.out.registro.ConsultarDiasProgramadosPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.DiaProgramado;
import com.renaser.os.habits.domain.model.registro.EstadoRegistro;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.renaser.os.habits.domain.model.registro.EstadoRegistro.COMPLETADO;
import static com.renaser.os.habits.domain.model.registro.EstadoRegistro.EXPIRADO;
import static com.renaser.os.habits.domain.model.registro.EstadoRegistro.PENDIENTE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-254: la lectura por paginas de la racha. Lo que se prueba aca es CUANTO se consulta —una pagina
 * para todos los habitos, y solo vuelven a pedir los que no encontraron su corte— y que la
 * paginacion no cambie el resultado. La regla en si la prueba {@code RachaDelHabitoTest}.
 */
class RachasDeHabitosTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 5);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final UserId PARTICIPANTE = UserId.of(UUID.randomUUID());

    private final HabitoId jugo = HabitoId.of(UUID.randomUUID());
    private final HabitoId dormir = HabitoId.of(UUID.randomUUID());
    private final DiasEnMemoria dias = new DiasEnMemoria();
    private final RachasDeHabitos rachas = new RachasDeHabitos(dias);

    @Test
    @DisplayName("todas se cortan en la primera pagina: UNA consulta para todos los habitos")
    void unaConsultaParaTodos() {
        dias.agregar(jugo, HOY, PENDIENTE).agregar(jugo, HOY.minusDays(1), COMPLETADO)
                .agregar(jugo, HOY.minusDays(2), EXPIRADO);
        dias.agregar(dormir, HOY, COMPLETADO).agregar(dormir, HOY.minusDays(1), EXPIRADO);

        Map<HabitoId, Integer> resultado = rachas.de(PARTICIPANTE, delDia(HOY.minusDays(60), jugo, dormir));

        assertThat(resultado).containsEntry(jugo, 1).containsEntry(dormir, 1);
        assertThat(dias.consultas).hasSize(1);
        assertThat(dias.consultas.get(0).habitos()).containsExactlyInAnyOrder(jugo, dormir);
        assertThat(dias.consultas.get(0).desde()).isEqualTo(HOY.minusDays(RachasDeHabitos.DIAS_POR_PAGINA - 1L));
        assertThat(dias.consultas.get(0).hasta()).isEqualTo(HOY);
    }

    @Test
    @DisplayName("una racha mas larga que una pagina pide la siguiente, solo para ese habito, y suma bien")
    void rachaLargaPideOtraPagina() {
        for (int i = 1; i <= 50; i++) {
            dias.agregar(jugo, HOY.minusDays(i), COMPLETADO);
        }
        dias.agregar(jugo, HOY.minusDays(51), EXPIRADO);
        dias.agregar(dormir, HOY.minusDays(1), EXPIRADO);

        Map<HabitoId, Integer> resultado = rachas.de(PARTICIPANTE, delDia(HOY.minusDays(80), jugo, dormir));

        assertThat(resultado).containsEntry(jugo, 50).containsEntry(dormir, 0);
        assertThat(dias.consultas).hasSize(2);
        assertThat(dias.consultas.get(1).habitos()).containsExactly(jugo);
        assertThat(dias.consultas.get(1).hasta()).isEqualTo(dias.consultas.get(0).desde().minusDays(1));
    }

    @Test
    @DisplayName("2A con una pausa mas larga que una pagina: la racha de antes de la pausa sigue contando")
    void pausaLarga() {
        dias.agregar(jugo, HOY, PENDIENTE).agregar(jugo, HOY.minusDays(1), COMPLETADO);
        // Pausado 40 dias: ninguna fila del 2 al 41. Antes, tres dias cumplidos y un vencido.
        dias.agregar(jugo, HOY.minusDays(42), COMPLETADO).agregar(jugo, HOY.minusDays(43), COMPLETADO)
                .agregar(jugo, HOY.minusDays(44), COMPLETADO).agregar(jugo, HOY.minusDays(45), EXPIRADO);

        assertThat(rachas.de(PARTICIPANTE, delDia(HOY.minusDays(80), jugo))).containsEntry(jugo, 4);
        assertThat(dias.consultas).hasSize(2);
    }

    @Test
    @DisplayName("no pide nada anterior al inicio del programa, y ahi se detiene aunque no haya corte")
    void seDetieneEnElInicio() {
        LocalDate inicio = HOY.minusDays(40);
        for (int i = 0; i <= 45; i++) {
            dias.agregar(jugo, HOY.minusDays(i), COMPLETADO);
        }

        assertThat(rachas.de(PARTICIPANTE, delDia(inicio, jugo))).containsEntry(jugo, 41);
        assertThat(dias.consultas).hasSize(2);
        assertThat(dias.consultas.get(1).desde()).isEqualTo(inicio);
    }

    @Test
    @DisplayName("un programa que todavia no empezo no consulta nada y da 0")
    void programaSinEmpezar() {
        dias.agregar(jugo, HOY, COMPLETADO);

        assertThat(rachas.de(PARTICIPANTE, delDia(HOY.plusDays(1), jugo))).containsEntry(jugo, 0);
        assertThat(dias.consultas).isEmpty();
    }

    @Test
    @DisplayName("sin inicio conocido mira 90 dias, como la racha general")
    void sinInicioConocido() {
        for (int i = 0; i <= 120; i++) {
            dias.agregar(jugo, HOY.minusDays(i), COMPLETADO);
        }

        assertThat(rachas.de(PARTICIPANTE, delDia(null, jugo))).containsEntry(jugo, 91);
        assertThat(dias.consultas.get(dias.consultas.size() - 1).desde()).isEqualTo(HOY.minusDays(90));
    }

    @Test
    @DisplayName("un dia sin registros no consulta nada")
    void diaVacio() {
        assertThat(rachas.de(PARTICIPANTE, new RegistrosDelDia(List.of(), HOY, LIMA, HOY.minusDays(10)))).isEmpty();
        assertThat(dias.consultas).isEmpty();
    }

    private RegistrosDelDia delDia(LocalDate inicio, HabitoId... habitos) {
        List<RegistroHabito> registros = new ArrayList<>();
        for (HabitoId habito : habitos) {
            registros.add(RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), PARTICIPANTE, habito, HOY,
                    12, TipoDia.TODOS, false, Instant.parse("2026-10-05T05:02:00Z")));
        }
        return new RegistrosDelDia(registros, HOY, LIMA, inicio);
    }

    /** El puerto contra una tabla en memoria, anotando cada consulta que le hacen. */
    private static final class DiasEnMemoria implements ConsultarDiasProgramadosPort {

        private final Map<HabitoId, List<DiaProgramado>> filas = new HashMap<>();
        private final List<Consulta> consultas = new ArrayList<>();

        DiasEnMemoria agregar(HabitoId habito, LocalDate fecha, EstadoRegistro estado) {
            filas.computeIfAbsent(habito, h -> new ArrayList<>()).add(new DiaProgramado(fecha, estado, false));
            return this;
        }

        @Override
        public Map<HabitoId, List<DiaProgramado>> deHabitosEntre(UserId participanteId, Collection<HabitoId> habitos,
                                                                 LocalDate desde, LocalDate hasta) {
            consultas.add(new Consulta(Set.copyOf(habitos), desde, hasta));
            Map<HabitoId, List<DiaProgramado>> pagina = new HashMap<>();
            for (HabitoId habito : habitos) {
                List<DiaProgramado> enRango = filas.getOrDefault(habito, List.of()).stream()
                        .filter(d -> !d.fecha().isBefore(desde) && !d.fecha().isAfter(hasta)).toList();
                if (!enRango.isEmpty()) {
                    pagina.put(habito, enRango);
                }
            }
            return pagina;
        }
    }

    private record Consulta(Set<HabitoId> habitos, LocalDate desde, LocalDate hasta) {
    }
}
