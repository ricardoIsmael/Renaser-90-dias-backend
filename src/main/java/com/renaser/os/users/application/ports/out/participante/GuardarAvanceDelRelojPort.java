package com.renaser.os.users.application.ports.out.participante;

import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;

/**
 * Lo que guarda el barrido del reloj (D-197): SOLO los campos que mueve
 * {@code ParticipacionPrograma.sincronizarDiaDelPrograma} (dia, fase, marca de avance y graduacion),
 * y SOLO si el ajuste de dia y la fecha de inicio siguen siendo los que se leyeron.
 *
 * <p><b>Por que no alcanza con {@link SaveParticipacionProgramaPort}.</b> El barrido lee paginas de
 * 500 y guarda despues cada fila ENTERA. Si un admin ajusta el dia de alguien entre esa lectura y
 * ese guardado (la ventana cae justo en la medianoche de Lima, cuando el barrido del minuto :05
 * escribe a casi todo el padron), el guardado pisaba {@code dias_ajuste_programa} con el valor
 * viejo: el ajuste se perdia en silencio, aunque la bitacora lo registrara.
 *
 * <p>Es una actualizacion condicional y no un {@code @Version}: no hace falta migracion, no cambia
 * nada para los demas escritores de la tabla y la carrera se resuelve en la base (con READ
 * COMMITTED, si el ajuste esta en vuelo el UPDATE espera su commit y reevalua la condicion).
 */
public interface GuardarAvanceDelRelojPort {

    /**
     * @return {@code false} si la fila cambio de ajuste o de fecha de inicio desde que se leyo (o ya
     *         no existe): no se escribio nada, y hay que releerla y volver a derivar
     */
    boolean guardarSiNoSeAjusto(ParticipacionPrograma participacion);
}
