package com.renaser.os.habits.api;

import com.renaser.os.habits.domain.model.horario.VentanaDelDia;
import com.renaser.os.shared.domain.UserId;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

/**
 * Alta de un habito PROPIO del aprendiz vista desde otro modulo (D-229, 2026-09-29).
 *
 * <p>Primer consumidor: la herramienta {@code proponer_crear_habito_personal} del acompanante de
 * {@code rag}, que solo la llama cuando la persona toca "Confirmar" en la tarjeta. Se expone aca
 * por la regla de siempre (D-41, regla 01): {@code rag} no escribe {@code habitos} ni
 * {@code horarios_habito}, y el alta pasa por el MISMO {@code CrearHabitoPersonalUseCase} que
 * {@code POST /api/v1/habits}, con todas sus guardas (participacion, suspension, dia de inicio de
 * D-103/D-216, ventana del dia de D-122, dias apagados).
 *
 * <p>Se crea igual que desde Training: tipo CHECKBOX, plantilla OTRO, sin icono y sin hora limite
 * (un habito propio no vence dentro del dia).
 *
 * <p>Lanza las mismas excepciones que ese caso de uso: {@code NoSuchElementException} sin
 * participacion en el programa, {@code NotAuthorizedException} con la cuenta suspendida, y
 * {@code IllegalArgumentException} (o {@code ConstraintViolationException}) si los datos no pasan
 * sus validaciones. Traducirlas a un texto es del llamador.
 */
public interface HabitosPersonalesPort {

    /**
     * La hora mas tarde a la que puede arrancar un habito (D-122). Se expone para que quien propone
     * no ofrezca una hora que el alta va a rechazar; la regla sigue viviendo en {@code VentanaDelDia}.
     */
    LocalTime ULTIMA_HORA_DE_DISPARO = VentanaDelDia.ULTIMA_HORA_DE_DISPARO;

    /** @return el id del habito creado */
    UUID crear(UserId actorId, HabitoPersonalNuevo nuevo);

    /**
     * @param categoriaClave una de las claves de {@code categorias_habito}: CUERPO, MENTE,
     *                       CONSCIENCIA, ESPIRITU (las mismas que manda Training)
     * @param dias           los dias en que corre; vacio o {@code null} = los siete
     * @param meta           la meta en texto ({@code etiqueta_meta}), opcional
     */
    record HabitoPersonalNuevo(String titulo, String categoriaClave, LocalTime hora, Set<DayOfWeek> dias,
                               String meta) {
    }
}
