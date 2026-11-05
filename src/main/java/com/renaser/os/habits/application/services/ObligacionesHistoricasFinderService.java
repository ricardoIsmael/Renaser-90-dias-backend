package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.MedicionDeLaObligacion;
import com.renaser.os.habits.api.MedicionDeLaObligacion.CarreraDeLaObligacion;
import com.renaser.os.habits.api.ObligacionHabito;
import com.renaser.os.habits.api.ObligacionesHistoricasFinder;
import com.renaser.os.habits.application.politica.PoliticaKilometros;
import com.renaser.os.habits.application.ports.out.carrera.LeerCarrerasPort;
import com.renaser.os.habits.application.ports.out.carrera.LeerCarrerasPort.ResumenDeCarrera;
import com.renaser.os.habits.application.ports.out.registro.ConsultarObligacionesHistoricasPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio propio y no un método más de los que ya existen, por el mismo criterio que
 * {@code evidence.RegistrosConEvidenciaFinderService}: es la única pieza de {@code habits} cuyo
 * consumidor está afuera, no escribe nada, y tenerla aparte deja a la vista qué expone el módulo.
 *
 * <p>Spec 042 (FR-044): a la obligación de KILÓMETROS DIARIOS de un día con carreras con GPS le suma la lista resumida de esas
 * carreras, con una consulta más para todo el rango (no una por día). Las de un día cuyo hábito no quedó cumplido con un número
 * (no contaron, el día ya había cerrado) también se ven: el mentor tiene que poder ver una carrera «por revisar».
 *
 * <p>Sin {@code @Transactional}: son lecturas.
 */
@Service
class ObligacionesHistoricasFinderService implements ObligacionesHistoricasFinder {

    private final ConsultarObligacionesHistoricasPort consultarPort;
    private final LeerCarrerasPort carrerasPort;

    ObligacionesHistoricasFinderService(ConsultarObligacionesHistoricasPort consultarPort, LeerCarrerasPort carrerasPort) {
        this.consultarPort = consultarPort;
        this.carrerasPort = carrerasPort;
    }

    @Override
    public List<ObligacionHabito> porParticipantesEntre(Collection<UserId> participantes, LocalDate desde,
                                                         LocalDate hasta) {
        if (participantes.isEmpty()) {
            return List.of();
        }
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("El rango va al reves: " + desde + " → " + hasta);
        }
        List<ObligacionHabito> obligaciones = consultarPort.entre(participantes, desde, hasta);
        if (obligaciones.stream().noneMatch(ObligacionesHistoricasFinderService::esDeKilometros)) {
            return obligaciones;
        }
        Map<DiaDePersona, List<CarreraDeLaObligacion>> carreras = carrerasPort.entre(participantes, desde, hasta).stream()
                .collect(Collectors.groupingBy(c -> new DiaDePersona(c.participanteId(), c.fecha()),
                        Collectors.mapping(ObligacionesHistoricasFinderService::aCarrera, Collectors.toList())));
        return obligaciones.stream().map(o -> conCarreras(o, carreras)).toList();
    }

    private static ObligacionHabito conCarreras(ObligacionHabito o, Map<DiaDePersona, List<CarreraDeLaObligacion>> carreras) {
        List<CarreraDeLaObligacion> delDia = carreras.get(new DiaDePersona(o.participanteId(), o.fecha()));
        if (!esDeKilometros(o) || delDia == null) {
            return o;
        }
        MedicionDeLaObligacion medicion = o.medicion() == null ? MedicionDeLaObligacion.soloCarreras(delDia)
                : o.medicion().conCarreras(delDia);
        return new ObligacionHabito(o.registroId(), o.participanteId(), o.fecha(), o.diaPrograma(), o.titulo(), o.estado(),
                o.requiereEvidencia(), o.opcional(), o.claveSistema(), o.respuestaTexto(), medicion);
    }

    private static boolean esDeKilometros(ObligacionHabito o) {
        return PoliticaKilometros.CLAVE_SISTEMA.equals(o.claveSistema());
    }

    private static CarreraDeLaObligacion aCarrera(ResumenDeCarrera c) {
        return new CarreraDeLaObligacion(c.carreraId(), c.kilometros(), c.duracionActivaSeg(), c.ritmoSegPorKm(), c.porRevisar(),
                c.cuentaParaElHabito(), c.tieneRuta());
    }

    private record DiaDePersona(UserId persona, LocalDate fecha) {
    }
}
