package com.renaser.os.rag.application.ports.out.habitos;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Puerto propio de {@code rag} para todo lo que las herramientas del agente necesitan saber y
 * hacer con los habitos del dia. {@code registros_habito} es tabla de {@code habits} — por las
 * reglas de Modulith (D-41), {@code rag} no la consulta de frente: el adaptador que implementa
 * este puerto delega en {@code habits.api.AgendaDelDiaFinder}.
 *
 * <p>Mismo criterio que {@code LeerEntradasDiarioPort}: el puerto nombra la intencion de negocio
 * de ESTE modulo y define su propio {@link HabitoDelDia}, sin exponer el tipo de {@code habits}.
 * Asi {@code rag.application} no acopla su firma a un contrato ajeno, y el dia que ese contrato
 * cambie la traduccion queda contenida en el adaptador.
 *
 * <p><b>Lectura y escritura en el mismo puerto, a proposito.</b> No es un repositorio generico:
 * son las tres operaciones que el agente puede hacer con la agenda de un aprendiz, ni una mas
 * (ISP se cumple por lo chico del contrato, no por partirlo en dos interfaces de un metodo).
 */
public interface ConsultarAgendaHabitosPort {

    /** Vacio si el aprendiz no tiene habitos hoy. */
    List<HabitoDelDia> deHoyDe(UserId participanteId);

    /**
     * Marca uno como hecho, con las mismas guardas que la app.
     *
     * @return los puntos otorgados
     * @throws RuntimeException si el registro no existe, no es suyo, ya esta en estado terminal o
     *                          se le vencio la ventana — el caso de uso lo traduce a un
     *                          {@code ResultadoHerramienta.Fallo} legible, nunca lo deja subir
     */
    int completar(UserId actorId, UUID registroId);

    /**
     * @param puntosEnJuego {@code null} cuando ya esta en estado terminal (nada en juego)
     * @param plazo         {@code null} cuando el habito no vence
     */
    record HabitoDelDia(UUID registroId, String titulo, String estado, Integer puntosEnJuego, Integer puntosMaximos,
                         Instant plazo) {

        /** Un habito con puntos en juego es, por definicion, uno que todavia se puede entregar. */
        public boolean sigueEnJuego() {
            return puntosEnJuego != null;
        }
    }
}
