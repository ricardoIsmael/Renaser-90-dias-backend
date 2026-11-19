package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDelAprendizDelMentorUseCase.SemanaDelAprendizParaElMentor;
import com.renaser.os.leadership.application.services.LecturaDeLaFicha.Lectura;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.leadership.domain.model.cumplimiento.SemanaDelMentor;
import com.renaser.os.leadership.domain.model.cumplimiento.VentanaDeLaFicha;
import com.renaser.os.users.api.ParticipacionPrograma;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;

/**
 * El armado de la semana de una persona de sábado a viernes —la del semáforo—, día por día y con el pilar de cada hábito (spec 055,
 * FR-011 y FR-013): la ventana ({@link SemanaDelMentor}), las lecturas de la ficha ({@link LecturaDeLaFicha}) y su armado
 * ({@link ArmadoDeLaFicha}), con el Día 1 que limita el mes. <b>No autoriza ni deja registro</b>: quien lo llama ya decidió quién puede
 * ver a esa persona. Lo usan la ficha del mentor ({@link SemanaDelAprendizService}) y la del líder ({@link SemanaDeLaPersonaService},
 * spec 056), así la semana, los colores y los hábitos de los dos no pueden diferir.
 */
@Component
class SemanaDeSabadoAViernes {

    private final LecturaDeLaFicha lecturas;

    SemanaDeSabadoAViernes(LecturaDeLaFicha lecturas) {
        this.lecturas = lecturas;
    }

    /**
     * @param semanaHasta el viernes que cierra la semana; null = la semana en curso de la persona
     * @param ahora       el instante de la consulta: el «hoy» que cuenta es el de la persona, en su zona (regla 02)
     * @throws com.renaser.os.shared.domain.RechazoConCodigoException {@code SEMANA_INVALIDA} o {@code FUERA_DEL_PROGRAMA}
     */
    SemanaDelAprendizParaElMentor armar(Mirada mirada, LocalDate semanaHasta, Instant ahora) {
        ParticipacionPrograma participacion = mirada.miembro().participacion();
        LocalDate hoy = ahora.atZone(participacion.zona()).toLocalDate();
        LocalDate diaUno = participacion.diaUnoElegido();
        VentanaDeLaFicha ventana = SemanaDelMentor.ventana(semanaHasta, hoy, diaUno);
        Lectura lectura = lecturas.leer(mirada, ventana);
        return new SemanaDelAprendizParaElMentor(ArmadoDeLaFicha.armar(mirada.miembro(), ventana, lectura), diaUno);
    }
}
