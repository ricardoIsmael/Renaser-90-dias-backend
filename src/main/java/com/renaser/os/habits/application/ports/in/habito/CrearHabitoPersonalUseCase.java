package com.renaser.os.habits.application.ports.in.habito;

import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * Alta de un habito PROPIO del aprendiz (ambito PERSONAL, tabla {@code habitos} unificada,
 * P-12). Autoservicio estricto: el {@code participanteId} sale SIEMPRE del actor autenticado,
 * nunca del cuerpo del request (CLAUDE.MD §5.3.3, mismo blindaje de mass-assignment que el
 * alta publica de {@code AccountRequest}) — por eso este comando no tiene un campo
 * {@code participanteId} ni {@code ambito}: el {@code ambito} lo fuerza la factoria
 * {@link Habito#crearPersonal} en el dominio, no un valor que viaje por HTTP.
 *
 * <p><b>Cierra el hueco de docs/informes/habits-eleccion-y-personales.md §3/§4.4:</b> un
 * habito PERSONAL sin {@link HorarioHabito} nunca generaba {@code registro_habito} (ver
 * {@code RegistroService.generarInterno}), y la unica via para crear un horario era el panel
 * admin. Decision del dueno del proyecto (2026-09-02): el propio aprendiz elige la hora al
 * crear el habito, asi que este comando ahora lleva {@code horaDisparo} (obligatoria) y
 * {@code horaLimite} (opcional, como la mayoria del catalogo) — {@code MisHabitosService.crear}
 * crea el {@code Habito} y su {@code HorarioHabito} en la MISMA transaccion, nunca uno sin el
 * otro.
 */
public interface CrearHabitoPersonalUseCase {

    Habito crear(CrearHabitoPersonalCommand command);

    record CrearHabitoPersonalCommand(@NotNull UserId actorId, @NotBlank @Size(max = 120) String titulo,
                                       @NotNull TipoHabito tipo, @NotBlank String categoriaClave,
                                       PlantillaHabitoPersonal plantilla, @Size(max = 200) String etiquetaMeta,
                                       @NotNull LocalTime horaDisparo, LocalTime horaLimite) {
        public CrearHabitoPersonalCommand {
            SelfValidating.validateConstructorArgs(CrearHabitoPersonalCommand.class, actorId, titulo, tipo,
                    categoriaClave, plantilla, etiquetaMeta, horaDisparo, horaLimite);
            // Nivel 2 de validacion (CLAUDE.MD §5.4.3): estructuralmente imposible construir un
            // comando con el habito ya vencido. La misma regla vive tambien en
            // HorarioHabito.crear (el agregado de dominio) — defensa en profundidad, no
            // duplicacion decorativa: este comando es el unico punto de entrada HTTP, pero el
            // agregado protege el invariante para CUALQUIER llamador (ej. un futuro admin que
            // edite el horario de un habito personal).
            if (horaLimite != null && !horaLimite.isAfter(horaDisparo)) {
                throw new IllegalArgumentException("horaLimite debe ser posterior a horaDisparo");
            }
        }
    }
}
