package com.renaser.os.habits.application.ports.out.habitosaprendiz;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Lectura por PROYECCION (CLAUDE.MD §11: JDBC puro para grillas de lectura, JPA para el
 * dominio que muta) de todo lo que un aprendiz tiene personalizado sobre sus habitos
 * activos: renombre, preferencia de horario, cambio programado, desbloqueo y dia semanal
 * elegido. Las seis tablas se resuelven en UNA consulta — nunca una por habito.
 *
 * <p>El puerto se nombra por intencion de negocio ("leer los habitos personalizados"), no
 * por tecnologia; quien delata que hay SQL detras es el adaptador
 * ({@code HabitosDeAprendizJdbcAdapter}).
 *
 * <p><b>Sin paginacion, a proposito:</b> la coleccion no crece sin techo — su tope real es
 * "los habitos activos de UNA persona": el catalogo de sistema activo (hoy ~25 filas, lo
 * fija el alquimista desde el panel, no los usuarios) mas los habitos personales de ese
 * aprendiz. Es exactamente el mismo conjunto que ya se materializa entero, sin paginar, en
 * {@code RegistroService.generar} una vez por dia y por persona. Paginar aqui obligaria al
 * panel a hacer N requests para mostrar una grilla que entra en una pantalla, y romperia
 * el calculo de cuota (que es por semana completa, no por pagina).
 */
public interface LeerHabitosPersonalizadosPort {

    /**
     * @param diaPrograma  dia de programa del aprendiz — decide que fila de
     *                     {@code horarios_habito} esta vigente
     * @param tipoDia      tipo de dia de hoy en la zona del aprendiz (un horario de tipo
     *                     {@code TODOS} aplica siempre; el resto solo si coincide)
     * @param semanaInicio lunes de la semana de calendario en curso — ancla de
     *                     {@code dias_semanales_habito}
     */
    List<FilaHabitoDeAprendiz> deAprendiz(UserId aprendizId, int diaPrograma, TipoDia tipoDia,
                                           LocalDate semanaInicio);

    /**
     * Fila cruda: trae el horario del catalogo y el de la preferencia POR SEPARADO. La
     * precedencia entre los dos es una regla de negocio y se resuelve en el caso de uso,
     * no en el SQL — asi hay un solo lugar donde esa regla vive y se puede testear sin
     * base de datos.
     */
    record FilaHabitoDeAprendiz(HabitoId habitoId, String tituloCatalogo, String tituloPersonal, boolean esPersonal,
                                 TipoHabito tipo, String categoriaClave, boolean eleccionDiaSemanal,
                                 LocalTime horaDisparoCatalogo, LocalTime horaLimiteCatalogo,
                                 LocalTime horaDisparoPreferencia, LocalTime horaLimitePreferencia,
                                 Boolean recordatorioActivo, Integer minutosRecordatorio,
                                 LocalTime horaDisparoPendiente, LocalTime horaLimitePendiente,
                                 LocalDate fechaEfectivaPendiente, Integer diaDesbloqueo,
                                 Boolean desbloqueoElegidoPorLaPersona, LocalDate diaSemanalElegido) {
    }
}
