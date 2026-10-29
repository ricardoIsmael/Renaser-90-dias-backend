package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * El Codigo Renaser de una persona visto desde otro modulo (spec 012, D-286): sus registros y su Mi Radar, para que el
 * lider de mentores, Administracion y el alquimista los lean en la ficha de la persona
 * ({@code leadership}, {@code GET /api/v1/leadership/groups/{g}/people/{p}/radar}).
 *
 * <p>Sin {@code actorId}, a proposito, igual que {@link ObligacionesHistoricasFinder}: quien llama ya autorizo. En el
 * consumidor de hoy, {@code leadership} comprobo que el actor es lider, administrador o alquimista activo y que la
 * persona es de ese grupo. Este contrato responde por el id que le den. Ningun tipo de {@code habits.domain} cruza esta
 * frontera.
 *
 * <p><b>Todo lo que viaja aca es texto personal que escribio la persona.</b> Quien lo reciba no lo loguea.
 */
public interface CodigoRenaserDePersonaFinder {

    /**
     * Una pagina del historial, del registro mas nuevo al mas viejo, con Mi Radar de la persona. Cursor y pagina como
     * {@code GET /api/v1/radar/history}: {@code cursor} nulo trae la mas reciente.
     */
    CodigoRenaserDePersona de(UserId personaId, Instant cursor, int tamanoPagina);

    /**
     * @param miRadar         nulo mientras la persona va por un dia menor que 8: en la primera semana Mi Radar no aplica
     * @param siguienteCursor el {@code creadoEn} del registro mas viejo de esta pagina, o nulo si no hay mas
     */
    record CodigoRenaserDePersona(MiRadarDeLaPersona miRadar, List<RegistroDelRadar> registros,
                                  Instant siguienteCursor) {

        public CodigoRenaserDePersona {
            registros = List.copyOf(registros);
        }
    }

    /**
     * @param horas las horas en punto (03 a 23) de TODOS los dias, de menor a mayor; vacia si esta apagado
     * @param dias  los dias de la semana con horas propias, por numero; un dia que no esta sigue {@code horas}
     */
    record MiRadarDeLaPersona(boolean encendido, List<Integer> horas, List<DiaDeLaPersona> dias) {

        public MiRadarDeLaPersona {
            horas = List.copyOf(horas);
            dias = List.copyOf(dias);
        }
    }

    /** @param dia lunes = 1 ... domingo = 7 @param horas las propias de ese dia; vacia = «Sin Radar ese dia» */
    record DiaDeLaPersona(int dia, List<Integer> horas) {

        public DiaDeLaPersona {
            horas = List.copyOf(horas);
        }
    }

    /** @param horaPrevista la hora local (0-23) a la que respondia, o nulo si fue un registro libre */
    record RegistroDelRadar(UUID id, String queHago, String quePienso, String queSiento, int nivelEnergia,
                            String queEvito, Instant creadoEn, Integer horaPrevista) {
    }
}
