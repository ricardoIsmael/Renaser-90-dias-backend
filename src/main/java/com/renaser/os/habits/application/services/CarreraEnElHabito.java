package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaKilometros;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.out.carrera.CarrerasPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.registro.LoadRegistroHabitoPort;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.carrera.Carrera;
import com.renaser.os.habits.domain.model.carrera.EfectoEnElHabito;
import com.renaser.os.habits.domain.model.carrera.ResultadoEnElHabito;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.habits.domain.model.politica.GestoCompletar;
import com.renaser.os.habits.domain.model.registro.CorteDeExpiracion;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Aplica una carrera recién guardada al hábito KILÓMETROS DIARIOS de SU día (spec 042, FR-022 y FR-023). Corre dentro de la
 * transacción de {@link CarreraService}.
 *
 * <ul>
 *   <li>PENDIENTE: lo cierra por {@link CompletarRegistroUseCase} con el gesto propio del hábito y origen GPS (cualquier km mayor
 *   que cero, decisión del dueño): puntos, ventana, evento y racha siguen en {@code RegistroService}, en un solo lugar.</li>
 *   <li>COMPLETADO por el teléfono (GPS o Health Connect): el mayor entre la suma de las carreras que cuentan y lo que ya tenía,
 *   nunca la suma de las dos fuentes; sin puntos ni evento.</li>
 *   <li>COMPLETADO con la captura: no se toca. Día cerrado (D-259), sin hábito o la carrera no cuenta: no se aplica.</li>
 * </ul>
 *
 * <p>El registro se lee CON cerrojo y como primera lectura de esa fila en la transacción (el invariante de
 * {@code RegistroService.completar}): dos carreras del mismo día se serializan y la segunda suma la primera.
 */
@Component
class CarreraEnElHabito {

    private final LoadHabitoPort habitos;
    private final LoadRegistroHabitoPort registros;
    private final SaveRegistroHabitoPort guardarRegistro;
    private final CompletarRegistroUseCase completar;
    private final CarrerasPort carreras;
    private final Clock clock;

    CarreraEnElHabito(LoadHabitoPort habitos, LoadRegistroHabitoPort registros, SaveRegistroHabitoPort guardarRegistro,
                      CompletarRegistroUseCase completar, CarrerasPort carreras, Clock clock) {
        this.habitos = habitos;
        this.registros = registros;
        this.guardarRegistro = guardarRegistro;
        this.completar = completar;
        this.carreras = carreras;
        this.clock = clock;
    }

    EfectoEnElHabito aplicar(Carrera carrera, Optional<ProgresoParticipanteHabits> progreso) {
        if (!carrera.evaluacion().cuenta()) {
            return EfectoEnElHabito.sinRegistro(ResultadoEnElHabito.NO_CUENTA);
        }
        if (progreso.isEmpty()) {
            return EfectoEnElHabito.sinRegistro(ResultadoEnElHabito.SIN_HABITO);
        }
        if (CorteDeExpiracion.para(ZoneId.of(progreso.get().timezone()), clock.now()).yaTermino(carrera.fecha())) {
            return EfectoEnElHabito.sinRegistro(ResultadoEnElHabito.DIA_CERRADO);
        }
        return registroDelDia(carrera, progreso.get())
                .map(registro -> aplicarAlRegistro(carrera, registro))
                .orElseGet(() -> EfectoEnElHabito.sinRegistro(ResultadoEnElHabito.SIN_HABITO));
    }

    private Optional<RegistroHabito> registroDelDia(Carrera carrera, ProgresoParticipanteHabits progreso) {
        Optional<Habito> habito = habitos.porClaveSistema(PoliticaKilometros.CLAVE_SISTEMA, progreso.formacion());
        return habito.flatMap(h -> registros.porParticipanteHabitoYFechaParaEscritura(carrera.participanteId(), h.id(),
                carrera.fecha()));
    }

    private EfectoEnElHabito aplicarAlRegistro(Carrera carrera, RegistroHabito registro) {
        return switch (registro.estado()) {
            case PENDIENTE, EN_CURSO -> cerrar(carrera, registro);
            case COMPLETADO -> subirSiCorresponde(carrera, registro);
            case EXPIRADO, FALLIDO -> new EfectoEnElHabito(ResultadoEnElHabito.DIA_CERRADO, registro.id().value(), null, null, 0);
        };
    }

    private EfectoEnElHabito cerrar(Carrera carrera, RegistroHabito registro) {
        BigDecimal km = carreras.kilometrosQueCuentan(carrera.participanteId(), carrera.fecha());
        RegistroHabito cerrado = completar.completar(new CompletarRegistroCommand(carrera.participanteId(), registro.id(), null,
                null, GestoCompletar.PROPIO_DEL_HABITO, MedicionDiaria.gps(km)));
        return efecto(ResultadoEnElHabito.CERRADO, cerrado, cerrado.puntosOtorgados());
    }

    private EfectoEnElHabito subirSiCorresponde(Carrera carrera, RegistroHabito registro) {
        MedicionDiaria actual = registro.medicion();
        if (actual == null || !actual.esDelTelefono()) {
            return efecto(ResultadoEnElHabito.YA_CON_CAPTURA, registro, 0);
        }
        BigDecimal km = carreras.kilometrosQueCuentan(carrera.participanteId(), carrera.fecha());
        if (MedicionDiaria.gps(km).valor().compareTo(actual.valor()) <= 0) {
            return efecto(ResultadoEnElHabito.SIN_CAMBIO, registro, 0);
        }
        registro.subirMedicion(MedicionDiaria.gps(km), clock.now());
        return efecto(ResultadoEnElHabito.ACTUALIZADO, guardarRegistro.save(registro), 0);
    }

    private static EfectoEnElHabito efecto(ResultadoEnElHabito resultado, RegistroHabito registro, int puntos) {
        MedicionDiaria medicion = registro.medicion();
        return new EfectoEnElHabito(resultado, registro.id().value(), medicion == null ? null : medicion.valor(),
                medicion == null ? null : medicion.origen(), puntos);
    }
}
