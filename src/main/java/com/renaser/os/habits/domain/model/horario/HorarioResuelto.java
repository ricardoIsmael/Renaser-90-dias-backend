package com.renaser.os.habits.domain.model.horario;

import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;

import java.time.LocalTime;

/**
 * El horario que de verdad rige para un habito y un aprendiz concretos: la preferencia del
 * participante si la tiene, y el horario del catalogo como respaldo, campo por campo.
 *
 * <p>Existe porque esa regla ("la preferencia gana si esta seteada; si no, el catalogo") ya
 * estaba escrita dos veces con dos formas distintas — {@code RegistroService.resolverVentana} y
 * {@code TracksDelDiaProyeccionService.construirVista} — y los avisos automaticos iban a ser la
 * tercera. Es una regla de negocio, no un detalle de cada consulta: vive en el dominio, se
 * prueba sin Spring y los llamadores la comparten.
 *
 * <p>El respaldo es <b>por campo y no por objeto</b>, a proposito: una preferencia que solo fija
 * la hora de inicio conserva la hora de cierre del catalogo. Colapsar los dos campos juntos
 * borraria el cierre en cuanto el aprendiz moviera el inicio.
 *
 * <p>Con una excepcion (D-230, 2026-09-29): si el cierre que queda del catalogo ya no es posterior
 * al inicio que eligio la persona (Pastilla Renacer 07:00-12:00 movida a las 22:00), no se lo
 * conserva tal cual — se acomoda con {@link VentanaDelDia#limiteQueSigueAlDisparo}. Cualquier
 * habito puede ir a cualquier hora del dia; la hora limite heredada no puede impedirlo.
 */
public record HorarioResuelto(LocalTime horaDisparo, LocalTime horaLimite) {

    /** Ambos argumentos pueden ser {@code null}: sin catalogo vigente, sin preferencia, o sin nada. */
    public static HorarioResuelto de(HorarioHabito delCatalogo, PreferenciaHorario delParticipante) {
        LocalTime disparo = delCatalogo != null ? delCatalogo.horaDisparo() : null;
        LocalTime limite = delCatalogo != null ? delCatalogo.horaLimite() : null;
        if (delParticipante != null && delParticipante.horaDisparo() != null) {
            disparo = delParticipante.horaDisparo();
        }
        if (delParticipante != null && delParticipante.horaLimite() != null) {
            limite = delParticipante.horaLimite();
        }
        // D-230: un limite del catalogo que quedo ANTES del disparo que eligio la persona no puede
        // armar una ventana que cruce la medianoche (22:00 a 12:00): se acomoda como en D-122.
        return new HorarioResuelto(disparo, VentanaDelDia.limiteQueSigueAlDisparo(disparo, limite));
    }

    /** Sin ninguna de las dos horas el habito no vence nunca — no hay ventana que calcular. */
    public boolean sinHorario() {
        return horaDisparo == null && horaLimite == null;
    }
}
