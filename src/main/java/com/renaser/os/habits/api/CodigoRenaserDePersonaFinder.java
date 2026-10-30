package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * El Codigo Renaser de una persona visto desde otro modulo (spec 012, D-286): sus registros y su Mi Radar (spec 019: lo de hoy), para que el
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
     * Mi Radar de la persona con lo de HOY (spec 019): su Despertar, su Dormir y cuantas veces pregunta.
     *
     * @param hoy  la fecha de hoy en la zona de la persona
     * @param dias solo el dia de hoy (una lista, con la misma forma que la lectura propia)
     */
    record MiRadarDeLaPersona(boolean encendido, LocalDate hoy, List<DiaDeLaPersona> dias) {

        public MiRadarDeLaPersona {
            dias = List.copyOf(dias);
        }
    }

    /**
     * @param dia      lunes = 1 ... domingo = 7
     * @param modo     {@code AUTOMATICO} o {@code PERSONALIZADO}
     * @param maximo   las horas en punto que caben entre su Despertar y su Dormir
     * @param veces    las que pregunta de verdad; 0 si ese dia no tiene Radar
     * @param motivo   {@code SIN_DESPERTAR}, {@code SIN_DORMIR} o {@code HORARIO_INVALIDO} si no hay horario; si no, nulo
     */
    record DiaDeLaPersona(int dia, LocalDate fecha, String modo, int maximo, int veces, boolean sinRadar,
                          boolean editable, LocalTime despertar, LocalTime dormir, List<Integer> horas,
                          String motivo) {

        public DiaDeLaPersona {
            horas = List.copyOf(horas);
        }
    }

    /** @param horaPrevista la hora local (0-23) a la que respondia, o nulo si fue un registro libre */
    record RegistroDelRadar(UUID id, String queHago, String quePienso, String queSiento, int nivelEnergia,
                            String queEvito, Instant creadoEn, Integer horaPrevista) {
    }
}
