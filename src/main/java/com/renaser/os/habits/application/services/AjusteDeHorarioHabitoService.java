package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.AjustarHorarioHabitoUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.CambiarEstadoHabitoEnFechaUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.ConsultarPreferenciasHorarioUseCase.HorarioDeHabito;
import com.renaser.os.habits.application.ports.in.preferencia.EditarHorarioSemanalUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.EditarPreferenciaHorarioCommand;
import com.renaser.os.habits.application.ports.in.preferencia.EditarPreferenciaHorarioUseCase.ResultadoEdicionPreferencia;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.CuotaEdicionHorario;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementa {@link AjustarHorarioHabitoUseCase} (2026-09-23). Fachada delgada, mismo patron que
 * {@link HorarioDelDiaFinderService}: cada metodo es UNA llamada al caso de uso que ya usa la app
 * por HTTP, y aca no se reimplementa ni la cuota, ni la precedencia, ni la regla de las fechas, ni
 * la de los obligatorios. Solo se traduce entre los tipos de la api y los de adentro.
 *
 * <p><b>Lo unico que suma es conservar el recordatorio.</b> El comando del PATCH lleva
 * {@code recordatorioActivo}/{@code minutosRecordatorio} y los escribe siempre; si el acompanante
 * mandara {@code false}/{@code null} apagaria el aviso de la persona en cada cambio de hora, el mismo
 * bug que tuvo el movil hasta el 2026-09-07 (ver {@code ConsultarPreferenciasHorarioUseCase}). Por eso
 * se leen los valores vigentes con el GET de siempre y se reenvian tal cual.
 */
@Service
public class AjusteDeHorarioHabitoService implements AjustarHorarioHabitoUseCase {

    private final EditarPreferenciaHorarioUseCase editarPreferencia;
    private final CambiarEstadoHabitoEnFechaUseCase cambiarEstadoEnFecha;
    private final EditarHorarioSemanalUseCase horarioSemanal;
    private final ConsultarPreferenciasHorarioUseCase consultarPreferencias;

    public AjusteDeHorarioHabitoService(EditarPreferenciaHorarioUseCase editarPreferencia,
                                        CambiarEstadoHabitoEnFechaUseCase cambiarEstadoEnFecha,
                                        EditarHorarioSemanalUseCase horarioSemanal,
                                        ConsultarPreferenciasHorarioUseCase consultarPreferencias) {
        this.editarPreferencia = editarPreferencia;
        this.cambiarEstadoEnFecha = cambiarEstadoEnFecha;
        this.horarioSemanal = horarioSemanal;
        this.consultarPreferencias = consultarPreferencias;
    }

    /** Una sola transaccion: el recordatorio que se lee es el mismo que se reescribe. */
    @Override
    @Transactional
    public CambioDeHorarioAplicado cambiarHorario(CambioDeHorario cambio) {
        HabitoId habitoId = HabitoId.of(cambio.habitoId());
        Optional<HorarioDeHabito> vigente = vigenteDe(cambio, habitoId);
        ResultadoEdicionPreferencia resultado = editarPreferencia.editar(new EditarPreferenciaHorarioCommand(
                cambio.participanteId(), habitoId, cambio.horaInicio(), cambio.horaLimite(),
                vigente.map(HorarioDeHabito::recordatorioActivo).orElse(false),
                vigente.map(HorarioDeHabito::minutosRecordatorio).orElse(null), cambio.fecha()));
        return new CambioDeHorarioAplicado(resultado.horaDisparo(), resultado.horaLimite(),
                resultado.fechaEfectivaDiferido(), resultado.cambiosUsados(), resultado.cambiosRestantes(),
                resultado.cambiosLimite(), CuotaEdicionHorario.PERIODO_LIBRE.equals(resultado.periodo()));
    }

    @Override
    public void cambiarEstadoDelDia(UserId participanteId, EstadoDelDia estado) {
        cambiarEstadoEnFecha.cambiarEstadoEnFecha(participanteId, HabitoId.of(estado.habitoId()), estado.fecha(),
                estado.activo());
    }

    @Override
    public void fijarDiaDeLaSemana(HorarioDeDiaDeLaSemana horario) {
        horarioSemanal.fijar(horario.participanteId(), HabitoId.of(horario.habitoId()), horario.diaSemana(),
                horario.horaInicio(), horario.horaLimite());
    }

    @Override
    public void apagarDiaDeLaSemana(UserId participanteId, UUID habitoId, DayOfWeek diaSemana) {
        horarioSemanal.apagar(participanteId, HabitoId.of(habitoId), diaSemana);
    }

    @Override
    public void quitarDiaDeLaSemana(UserId participanteId, UUID habitoId, DayOfWeek diaSemana) {
        horarioSemanal.quitar(participanteId, HabitoId.of(habitoId), diaSemana);
    }

    /**
     * El horario que rige el dia que se va a tocar (hoy para un cambio general), leido con el mismo
     * GET de la app. Vacio si el habito no esta entre los activos: el PATCH lo va a rechazar igual.
     */
    private Optional<HorarioDeHabito> vigenteDe(CambioDeHorario cambio, HabitoId habitoId) {
        return consultarPreferencias.consultar(cambio.participanteId(), cambio.fecha()).habitos().stream()
                .filter(horario -> horario.habitoId().equals(habitoId))
                .findFirst();
    }
}
