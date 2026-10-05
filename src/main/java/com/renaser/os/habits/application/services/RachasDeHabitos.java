package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaUseCase.RegistrosDelDia;
import com.renaser.os.habits.application.ports.out.registro.ConsultarDiasProgramadosPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.registro.DiaProgramado;
import com.renaser.os.habits.domain.model.registro.RachaDelHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * D-254 — la racha de cada habito de un dia, para la agenda ({@code GET /habit-tracks/today}). La
 * regla vive en {@link RachaDelHabito}; esto solo trae los dias y se la pregunta.
 *
 * <p><b>Por paginas hacia atras, nunca N+1.</b> Cada vuelta es UNA consulta por todos los habitos
 * todavia abiertos y {@value #DIAS_POR_PAGINA} dias de calendario. Despues de cada pagina, un habito
 * cuya racha ya encontro el dia que la corta sale de la lista: lo anterior no la puede cambiar. Lo
 * normal es una sola consulta; un habito con una racha larga (o una pausa larga en el medio) pide
 * otra pagina mas vieja, hasta el inicio del programa.
 *
 * <p>"Hoy" es {@code dia.fecha()}, que la lectura del dia ya resolvio en la zona del participante con
 * el mismo progreso que autorizo (E-91, E-105). Nada aca vuelve a mirar el reloj.
 */
@Component
public class RachasDeHabitos {

    /** Cinco semanas por consulta: alcanza para casi toda racha, y una semanal ve cinco ocurrencias. */
    static final int DIAS_POR_PAGINA = 35;
    /** Sin inicio de programa conocido, la misma ventana que la racha general ({@code RachaMostrada}). */
    static final int DIAS_SIN_INICIO_CONOCIDO = 90;

    private final ConsultarDiasProgramadosPort diasProgramadosPort;

    public RachasDeHabitos(ConsultarDiasProgramadosPort diasProgramadosPort) {
        this.diasProgramadosPort = diasProgramadosPort;
    }

    /** @return por cada habito con registro en {@code dia}, su racha en dias (0 si no tiene). */
    public Map<HabitoId, Integer> de(UserId participanteId, RegistrosDelDia dia) {
        LocalDate hoy = dia.fecha();
        LocalDate inicio = dia.inicioDelPrograma() != null ? dia.inicioDelPrograma()
                : hoy.minusDays(DIAS_SIN_INICIO_CONOCIDO);
        Set<HabitoId> abiertos = dia.registros().stream().map(RegistroHabito::habitoId)
                .collect(Collectors.toCollection(HashSet::new));
        Lectura lectura = new Lectura(hoy, dia.inicioDelPrograma(), abiertos);
        LocalDate hasta = hoy;
        while (!abiertos.isEmpty() && !hasta.isBefore(inicio)) {
            LocalDate desde = masTarde(inicio, hasta.minusDays(DIAS_POR_PAGINA - 1L));
            lectura.agregar(diasProgramadosPort.deHabitosEntre(participanteId, Set.copyOf(abiertos), desde, hasta));
            abiertos.removeIf(lectura::quedoDefinitiva);
            hasta = desde.minusDays(1);
        }
        return lectura.rachas();
    }

    private static LocalDate masTarde(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    /** Lo leido hasta ahora y la racha de cada habito con eso: se recalcula entera en cada pagina. */
    private static final class Lectura {

        private final LocalDate hoy;
        private final LocalDate inicioDelPrograma;
        private final Map<HabitoId, List<DiaProgramado>> leidos = new HashMap<>();
        private final Map<HabitoId, Integer> rachas = new HashMap<>();

        Lectura(LocalDate hoy, LocalDate inicioDelPrograma, Set<HabitoId> habitos) {
            this.hoy = hoy;
            this.inicioDelPrograma = inicioDelPrograma;
            habitos.forEach(habito -> rachas.put(habito, 0));
        }

        void agregar(Map<HabitoId, List<DiaProgramado>> pagina) {
            pagina.forEach((habito, dias) -> leidos.computeIfAbsent(habito, h -> new ArrayList<>()).addAll(dias));
        }

        /** Recalcula la racha del habito; {@code true} si lo mas viejo ya no la puede cambiar. */
        boolean quedoDefinitiva(HabitoId habito) {
            RachaDelHabito racha = RachaDelHabito.derivar(leidos.getOrDefault(habito, List.of()), hoy,
                    inicioDelPrograma);
            rachas.put(habito, racha.dias());
            return racha.definitiva();
        }

        Map<HabitoId, Integer> rachas() {
            return rachas;
        }
    }
}
