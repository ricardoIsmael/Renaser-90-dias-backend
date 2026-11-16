package com.renaser.os.habits.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.habits.application.ports.out.preferencia.LoadPreferenciaHorarioPort;
import com.renaser.os.habits.application.ports.out.preferencia.SavePreferenciaHorarioPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.AjusteDeAlarma;
import com.renaser.os.habits.domain.model.preferencia.FechaPropia;
import com.renaser.os.habits.domain.model.preferencia.HorarioPorFecha;
import com.renaser.os.habits.domain.model.preferencia.HorarioSemanal;
import com.renaser.os.habits.domain.model.preferencia.AntelacionesDelRecordatorio;
import com.renaser.os.habits.domain.model.preferencia.PreferenciaHorario;
import com.renaser.os.habits.domain.model.preferencia.RecordatorioDelDia;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
class PreferenciaHorarioPersistenceAdapter implements LoadPreferenciaHorarioPort, SavePreferenciaHorarioPort {

    private final SpringDataPreferenciaHorarioRepository repository;
    private final PreferenciaHorarioPersistenceMapper mapper;
    private final SpringDataHorarioPorFechaRepository fechasRepository;
    private final SpringDataHorarioSemanalRepository semanalRepository;
    private final SpringDataCambioHorarioPendienteRepository pendientesRepository;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entityManager;

    PreferenciaHorarioPersistenceAdapter(SpringDataPreferenciaHorarioRepository repository,
                                          PreferenciaHorarioPersistenceMapper mapper,
                                          SpringDataHorarioPorFechaRepository fechasRepository,
                                          SpringDataHorarioSemanalRepository semanalRepository,
                                          SpringDataCambioHorarioPendienteRepository pendientesRepository,
                                          Clock clock, jakarta.persistence.EntityManager entityManager) {
        this.repository = repository;
        this.mapper = mapper;
        this.fechasRepository = fechasRepository;
        this.semanalRepository = semanalRepository;
        this.pendientesRepository = pendientesRepository;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<PreferenciaHorario> porParticipanteYHabito(UserId participanteId, HabitoId habitoId) {
        return repository.findByParticipanteIdAndHabitoId(participanteId.value(), habitoId.value())
                .map(mapper::toDomain);
    }

    @Override
    public List<PreferenciaHorario> porParticipanteYHabitos(UserId participanteId, Collection<HabitoId> habitoIds) {
        if (habitoIds.isEmpty()) {
            return List.of();
        }
        List<UUID> valores = habitoIds.stream().map(HabitoId::value).toList();
        return repository.findByParticipanteIdAndHabitoIdIn(participanteId.value(), valores).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public PreferenciaHorario save(PreferenciaHorario preferencia) {
        return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(preferencia)));
    }
    @Override
    public Optional<PreferenciaHorario> porParticipanteHabitoYFecha(UserId participanteId, HabitoId habitoId,
                                                                  LocalDate fecha) {
        return porParticipanteHabitosYFecha(participanteId, List.of(habitoId), fecha).stream().findFirst();
    }

    @Override
    public List<PreferenciaHorario> porParticipanteHabitosYFecha(UserId participanteId, Collection<HabitoId> habitoIds,
                                                               LocalDate fecha) {
        return resolverPorCapas(participanteId, habitoIds, fecha, true);
    }

    @Override
    public List<PreferenciaHorario> porParticipanteHabitosYFechaSinAvisoDelDia(UserId participanteId,
                                                                               Collection<HabitoId> habitoIds,
                                                                               LocalDate fecha) {
        return resolverPorCapas(participanteId, habitoIds, fecha, false);
    }

    /**
     * Las capas, de la mas general a la mas especifica: preferencia general, cambio general ya vigente
     * (D-91), dia de la semana y fecha exacta. {@code conAvisoDelDia = false} deja fuera SOLO el aviso
     * propio de la capa del dia de la semana (spec 008) y de la fecha (spec 028): lo demas, hora incluida, es igual.
     */
    private List<PreferenciaHorario> resolverPorCapas(UserId participanteId, Collection<HabitoId> habitoIds,
                                                      LocalDate fecha, boolean conAvisoDelDia) {
        if (habitoIds.isEmpty()) return List.of();
        Map<HabitoId, PreferenciaHorario> efectivos = new HashMap<>();
        porParticipanteYHabitos(participanteId, habitoIds).forEach(p -> efectivos.put(p.habitoId(), p));
        // Conserva los cambios generales de clientes anteriores, solo desde su fecha efectiva.
        for (var p : pendientesRepository.findByParticipanteId(participanteId.value())) {
            HabitoId id = HabitoId.of(p.getHabitoId());
            if (habitoIds.contains(id) && !p.getFechaEfectiva().isAfter(fecha)) {
                var anterior = efectivos.get(id);
                Integer minutos = anterior != null ? anterior.minutosRecordatorio() : null;
                if (p.getMinutosRecordatorio() != null) minutos = p.getMinutosRecordatorio().intValue();
                boolean activo = p.getRecordatorioActivo() != null ? p.getRecordatorioActivo()
                        : anterior != null && anterior.recordatorioActivo();
                efectivos.put(id, PreferenciaHorario.rehydrate(participanteId, id, p.getHoraDisparo(), p.getHoraLimite(),
                        activo, minutos, conjuntoTras(anterior, activo, minutos), alarmaDe(anterior),
                        p.getCreadoEn(), p.getCreadoEn()));
            }
        }
        var ids = habitoIds.stream().map(HabitoId::value).toList();
        // V39: la hora propia de ESE dia de la semana. Va entre el cambio general y la excepcion de
        // fecha exacta, que es lo que corresponde por especificidad: "los lunes a las 5" pisa al
        // horario general, y "el lunes 8 a las 4" pisa a los dos.
        //
        // El respaldo es POR CAMPO y no por objeto: una fila que solo fija la hora de disparo
        // conserva la hora limite de la capa de abajo. Reemplazar el objeto entero le borraria la
        // hora limite a los habitos que si vencen dentro del dia.
        //
        // V111 (spec 008): el AVISO del dia es otro campo y se resuelve igual, por separado de la hora.
        // Una fila de solo aviso deja la hora de abajo; una de solo hora deja el aviso de abajo.
        short diaIso = (short) fecha.getDayOfWeek().getValue();
        for (var p : semanalRepository.findByParticipanteIdAndHabitoIdInAndDiaSemana(
                participanteId.value(), ids, diaIso)) {
            HabitoId id = HabitoId.of(p.getHabitoId());
            boolean conHora = p.getHoraDisparo() != null;
            // Un dia APAGADO no avisa: si trae un aviso guardado, queda ahi y no se aplica.
            boolean conAviso = conAvisoDelDia && p.isActivo() && p.getRecordatorioActivo() != null;
            // V40: una fila que solo APAGA el día no trae hora, y no tiene que pisar la que regía.
            // Sin este corte, apagar los martes le borraría el horario a los martes.
            if (!conHora && !conAviso) {
                continue;
            }
            efectivos.put(id, conCapaDelDia(efectivos.get(id), p, conHora, conAviso));
        }
        for (var p : fechasRepository.findByParticipanteIdAndHabitoIdInAndFecha(participanteId.value(), ids, fecha)) {
            HabitoId id = HabitoId.of(p.getHabitoId());
            boolean conHora = p.getHoraDisparo() != null;
            // Spec 028 (V131): el AVISO de la fecha es otro campo y se resuelve por separado de la hora, igual que el del dia de la
            // semana. Una fila de solo aviso no pisa la hora de abajo; una fila que hereda el aviso (NULL) no pisa el aviso de abajo.
            // Un dia APAGADO no avisa. La lista de preferencias lo deja fuera: su aviso es el GENERAL del habito.
            boolean conAviso = conAvisoDelDia && p.isActivo() && p.getRecordatorioActivo() != null;
            // V38: una fila que solo APAGA el dia (o que solo trae como suena la alarma) no trae hora ni aviso, y no tiene que pisar
            // la que ya regia. Sin este corte, apagar el jueves le borraria el horario al jueves.
            if (!conHora && !conAviso) {
                continue;
            }
            efectivos.put(id, conCapaDeLaFecha(participanteId, efectivos.get(id), p, conHora, conAviso));
        }
        return List.copyOf(efectivos.values());
    }

    /**
     * H-01: las mismas capas de {@link #resolverPorCapas} (general, cambio general vigente, dia de la semana y fecha exacta, de la mas general a la mas
     * especifica) para UN habito y VARIAS personas, con una consulta por capa en vez de cuatro por persona. Solo la hora: las capas de aviso no entran
     * ({@code conAvisoDelDia = false}), y las funciones que mezclan cada capa son las MISMAS que usa la lectura de una sola persona.
     */
    @Override
    public Map<UserId, PreferenciaHorario> horaDeUnHabitoDeVarios(Collection<UserId> participantes, HabitoId habitoId, LocalDate fecha) {
        if (participantes.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = participantes.stream().map(UserId::value).distinct().toList();
        Map<UserId, PreferenciaHorario> efectivos = new HashMap<>();
        repository.findByParticipanteIdInAndHabitoId(ids, habitoId.value()).forEach(p -> efectivos.put(UserId.of(p.getParticipanteId()),
                mapper.toDomain(p)));
        for (var p : pendientesRepository.findByParticipanteIdInAndHabitoId(ids, habitoId.value())) {
            if (p.getFechaEfectiva().isAfter(fecha)) {
                continue;
            }
            UserId persona = UserId.of(p.getParticipanteId());
            var anterior = efectivos.get(persona);
            Integer minutos = anterior != null ? anterior.minutosRecordatorio() : null;
            if (p.getMinutosRecordatorio() != null) minutos = p.getMinutosRecordatorio().intValue();
            boolean activo = p.getRecordatorioActivo() != null ? p.getRecordatorioActivo()
                    : anterior != null && anterior.recordatorioActivo();
            efectivos.put(persona, PreferenciaHorario.rehydrate(persona, habitoId, p.getHoraDisparo(), p.getHoraLimite(), activo, minutos,
                    conjuntoTras(anterior, activo, minutos), alarmaDe(anterior), p.getCreadoEn(), p.getCreadoEn()));
        }
        short diaIso = (short) fecha.getDayOfWeek().getValue();
        for (var p : semanalRepository.findByParticipanteIdInAndHabitoIdAndDiaSemana(ids, habitoId.value(), diaIso)) {
            if (p.getHoraDisparo() == null) {
                continue; // una fila que solo apaga el dia no trae hora y no pisa la que regia (V40)
            }
            UserId persona = UserId.of(p.getParticipanteId());
            efectivos.put(persona, conCapaDelDia(efectivos.get(persona), p, true, false));
        }
        for (var p : fechasRepository.findByParticipanteIdInAndHabitoIdAndFecha(ids, habitoId.value(), fecha)) {
            if (p.getHoraDisparo() == null) {
                continue; // una fila que solo apaga la fecha (o trae como suena) no trae hora (V38)
            }
            UserId persona = UserId.of(p.getParticipanteId());
            efectivos.put(persona, conCapaDeLaFecha(persona, efectivos.get(persona), p, true, false));
        }
        return efectivos;
    }

    /**
     * La capa de la FECHA exacta sobre lo que ya regia, campo por campo (spec 028). Una fila con hora reemplaza la hora y el cierre, como
     * siempre (V37): un cierre nulo es «sin cierre propio». El aviso, si lo trae, es el de la fecha: sus antelaciones (V130) o, en una
     * fila de antes, su numero de minutos con el conjunto que ya regia si coincide (D-217).
     */
    private static PreferenciaHorario conCapaDeLaFecha(UserId participanteId, PreferenciaHorario anterior,
                                                       HorarioPorFechaJpaEntity fila, boolean conHora, boolean conAviso) {
        HabitoId id = HabitoId.of(fila.getHabitoId());
        LocalTime disparo = conHora ? fila.getHoraDisparo() : (anterior != null ? anterior.horaDisparo() : null);
        LocalTime limite = conHora ? fila.getHoraLimite() : (anterior != null ? anterior.horaLimite() : null);
        boolean activo = anterior != null && anterior.recordatorioActivo();
        Integer minutos = anterior != null ? anterior.minutosRecordatorio() : null;
        List<Integer> conjunto = anterior != null ? anterior.antelacionesRecordatorio() : null;
        if (conAviso) {
            activo = fila.getRecordatorioActivo();
            List<Integer> propias = PreferenciaHorarioPersistenceMapper.aLista(fila.getAntelacionesRecordatorio());
            if (propias != null && !propias.isEmpty() && activo) {
                minutos = propias.getFirst();
                conjunto = propias;
            } else {
                minutos = fila.getMinutosRecordatorio() == null ? null : fila.getMinutosRecordatorio().intValue();
                conjunto = conjuntoTras(anterior, activo, minutos);
            }
        }
        return PreferenciaHorario.rehydrate(participanteId, id, disparo, limite, activo, minutos, conjunto, alarmaDe(anterior),
                fila.getCreadoEn(), fila.getActualizadoEn());
    }

    /** La capa del dia de la semana sobre lo que ya regia, campo por campo (hora y aviso por separado). */
    private static PreferenciaHorario conCapaDelDia(PreferenciaHorario anterior, HorarioSemanalJpaEntity fila,
                                                    boolean conHora, boolean conAviso) {
        LocalTime horaLimiteDeAbajo = anterior != null ? anterior.horaLimite() : null;
        LocalTime disparo = conHora ? fila.getHoraDisparo() : (anterior != null ? anterior.horaDisparo() : null);
        LocalTime limite = conHora && fila.getHoraLimite() != null ? fila.getHoraLimite() : horaLimiteDeAbajo;
        boolean activo = anterior != null && anterior.recordatorioActivo();
        Integer minutos = anterior != null ? anterior.minutosRecordatorio() : null;
        List<Integer> antelaciones = anterior != null ? anterior.antelacionesRecordatorio() : null;
        if (conAviso) {
            RecordatorioDelDia propio = recordatorioDe(fila);
            activo = propio.activo();
            // D-217: los minutos son la antelacion mas temprana; sin aviso no hay minutos ni conjunto.
            minutos = propio.minutosMasTemprana();
            antelaciones = propio.activo() ? propio.antelaciones() : null;
        }
        return PreferenciaHorario.rehydrate(UserId.of(fila.getParticipanteId()), HabitoId.of(fila.getHabitoId()),
                disparo, limite, activo, minutos, antelaciones, alarmaDe(anterior), fila.getCreadoEn(),
                fila.getActualizadoEn());
    }

    /**
     * La alarma GENERAL que arrastra la preferencia resuelta (spec 009): las capas de fecha y de dia solo
     * deciden hora y aviso, asi que la combinacion pasa tal cual. La del dia de la semana va aparte, en
     * {@link HorarioSemanal#alarma()}.
     */
    private static AjusteDeAlarma alarmaDe(PreferenciaHorario anterior) {
        return anterior == null ? null : anterior.alarma();
    }

    /** {@code null} = la fila no trae alarma propia: el dia hereda la general. */
    private static AjusteDeAlarma alarmaDe(HorarioSemanalJpaEntity fila) {
        return PreferenciaHorarioPersistenceMapper.alarmaDe(fila.getAlarmaSonido(), fila.getAlarmaVibracion(),
                fila.getAlarmaVoz(), fila.getAlarmaRepetir());
    }

    /** {@code null} = la fila no trae aviso propio: el dia hereda el general. */
    private static RecordatorioDelDia recordatorioDe(HorarioSemanalJpaEntity fila) {
        if (fila.getRecordatorioActivo() == null) {
            return null;
        }
        return fila.getRecordatorioActivo()
                ? RecordatorioDelDia.con(PreferenciaHorarioPersistenceMapper.aLista(fila.getAntelacionesRecordatorio()))
                : RecordatorioDelDia.sinAviso();
    }

    private static HorarioSemanal aHorarioSemanal(HorarioSemanalJpaEntity fila) {
        var preferencia = PreferenciaHorario.rehydrate(UserId.of(fila.getParticipanteId()),
                HabitoId.of(fila.getHabitoId()), fila.getHoraDisparo(), fila.getHoraLimite(), false, null,
                fila.getCreadoEn(), fila.getActualizadoEn());
        return new HorarioSemanal(DayOfWeek.of(fila.getDiaSemana()), preferencia, fila.isActivo(),
                recordatorioDe(fila), alarmaDe(fila));
    }

    /**
     * D-217: las capas de abajo (cambio pendiente, excepción de una fecha) guardan solo los minutos. El
     * conjunto de la preferencia general sigue valiendo si esos minutos son su más temprana; si no, se
     * sabe solo ese número. Misma regla que el dominio ({@link AntelacionesDelRecordatorio}).
     */
    private static List<Integer> conjuntoTras(PreferenciaHorario anterior, boolean activo, Integer minutos) {
        return AntelacionesDelRecordatorio.trasMinutosSueltos(
                anterior != null ? anterior.antelacionesRecordatorio() : null,
                anterior != null ? anterior.minutosRecordatorio() : null, activo, minutos);
    }

    @Override
    public void saveParaFecha(HorarioPorFecha horario) {
        var p = horario.preferencia();
        var key = new HorarioPorFechaPk(p.participanteId().value(), p.habitoId().value(), horario.fecha());
        var existente = fechasRepository.findById(key);
        var creadoEn = existente.map(HorarioPorFechaJpaEntity::getCreadoEn).orElse(p.creadoEn());
        // Esta es la ruta de antes (spec 037): hora y aviso propios, enteros. Lo que la fecha pudiera tener de «Como suena» (V130) no es
        // suyo: se conserva. Las antelaciones solo viajan con el aviso activo (el CHECK de V130).
        var fila = new HorarioPorFechaJpaEntity(p.participanteId().value(), p.habitoId().value(), horario.fecha(),
                p.horaDisparo(), horario.activo(), p.horaLimite(), p.recordatorioActivo(),
                p.minutosRecordatorio() == null ? null : p.minutosRecordatorio().shortValue(), creadoEn, p.actualizadoEn());
        if (p.recordatorioActivo()) {
            fila.setAntelacionesRecordatorio(PreferenciaHorarioPersistenceMapper.aArreglo(p.antelacionesRecordatorio()));
        }
        existente.ifPresent(anterior -> {
            fila.setAlarmaSonido(anterior.getAlarmaSonido());
            fila.setAlarmaVibracion(anterior.getAlarmaVibracion());
            fila.setAlarmaVoz(anterior.getAlarmaVoz());
            fila.setAlarmaRepetir(anterior.getAlarmaRepetir());
        });
        fechasRepository.saveAndFlush(fila);
    }

    @Override
    public List<FechaPropia> fechasPropiasEntre(UserId participanteId, LocalDate desde, LocalDate hasta) {
        return fechasRepository.findByParticipanteIdAndFechaBetween(participanteId.value(), desde, hasta).stream()
                .map(PreferenciaHorarioPersistenceAdapter::aFechaPropia).toList();
    }

    @Override
    public List<FechaPropia> fechasPropiasDeUnHabitoEntre(UserId participanteId, HabitoId habitoId, LocalDate desde,
                                                          LocalDate hasta) {
        return fechasRepository.findByParticipanteIdAndHabitoIdAndFechaBetween(participanteId.value(), habitoId.value(), desde, hasta)
                .stream().map(PreferenciaHorarioPersistenceAdapter::aFechaPropia).toList();
    }

    /**
     * Auditoria de consultas del 2026-11-02 (H-06): antes, por fila, un {@code findById} y un {@code saveAndFlush} (que hace {@code merge} = un SELECT, y un
     * flush) → hasta 140 filas son 300 a 420 viajes. Ahora: UNA consulta trae lo que ya existia en esas fechas (de ahi sale {@code creado_en} y esas filas
     * quedan gestionadas), lo que ya existia se actualiza en su sitio, lo nuevo se inserta con {@code persist} (sin SELECT previo), y se hace UN flush.
     * Con {@code hibernate.jdbc.batch_size} las sentencias salen en lotes.
     */
    @Override
    @Transactional
    public void guardarFechasPropias(UserId participanteId, Collection<FechaPropia> filas) {
        guardarFechasPropias(participanteId, filas, clock.now());
    }

    @Override
    @Transactional
    public void guardarFechasPropias(UserId participanteId, Collection<FechaPropia> filas, java.time.Instant ahora) {
        if (filas.isEmpty()) {
            return;
        }
        LocalDate desde = filas.stream().map(FechaPropia::fecha).min(java.util.Comparator.naturalOrder()).orElseThrow();
        LocalDate hasta = filas.stream().map(FechaPropia::fecha).max(java.util.Comparator.naturalOrder()).orElseThrow();
        Map<HabitoYFecha, HorarioPorFechaJpaEntity> existentes = new HashMap<>();
        fechasRepository.findByParticipanteIdAndFechaBetween(participanteId.value(), desde, hasta)
                .forEach(e -> existentes.put(new HabitoYFecha(e.getHabitoId(), e.getFecha()), e));
        for (FechaPropia f : filas) {
            var previa = existentes.get(new HabitoYFecha(f.habitoId().value(), f.fecha()));
            var creadoEn = previa == null ? ahora : previa.getCreadoEn();
            RecordatorioDelDia aviso = f.recordatorio();
            AjusteDeAlarma alarma = f.alarma();
            var fila = new HorarioPorFechaJpaEntity(participanteId.value(), f.habitoId().value(), f.fecha(), f.horaDisparo(),
                    f.activo(), f.horaLimite(), activoDe(aviso),
                    aviso == null || !aviso.activo() ? null : aviso.minutosMasTemprana().shortValue(), creadoEn, ahora,
                    antelacionesDe(aviso), alarma == null ? null : alarma.sonido(), alarma == null ? null : alarma.vibracion(),
                    alarma == null ? null : alarma.voz(), alarma == null ? null : alarma.repetir());
            if (previa == null) {
                entityManager.persist(fila);
            } else {
                entityManager.merge(fila); // `previa` ya esta gestionada: se copia sobre ella, sin consulta
            }
        }
        entityManager.flush();
    }

    /** La clave de una excepcion dentro de UNA persona: el habito y el dia. */
    private record HabitoYFecha(UUID habitoId, LocalDate fecha) {
    }

    @Override
    @Transactional
    public void borrarParaFechas(UserId participanteId, Collection<FechaABorrar> fechas) {
        Map<UUID, java.util.Set<LocalDate>> porHabito = new HashMap<>();
        fechas.forEach(f -> porHabito.computeIfAbsent(f.habitoId().value(), id -> new java.util.HashSet<>()).add(f.fecha()));
        porHabito.forEach((habito, dias) -> fechasRepository.borrarDeUnHabitoEn(participanteId.value(), habito, dias));
    }

    @Override
    public int borrarFechasPropias(UserId participanteId, Collection<HabitoId> habitoIds, LocalDate desde, LocalDate hasta) {
        if (habitoIds.isEmpty()) {
            return fechasRepository.borrarEntre(participanteId.value(), desde, hasta);
        }
        int borradas = 0;
        for (HabitoId habito : habitoIds) {
            borradas += fechasRepository.borrarDeUnHabitoEntre(participanteId.value(), habito.value(), desde, hasta);
        }
        return borradas;
    }

    /** Una fila de la tabla a lo que la persona decidio para esa fecha (spec 028). Las columnas nuevas en NULL son «hereda». */
    private static FechaPropia aFechaPropia(HorarioPorFechaJpaEntity fila) {
        return new FechaPropia(HabitoId.of(fila.getHabitoId()), fila.getFecha(), fila.isActivo(), fila.getHoraDisparo(),
                fila.getHoraLimite(), recordatorioDe(fila), alarmaDe(fila));
    }

    /**
     * {@code null} = la fila hereda el aviso. Una fila de antes de V130 trae un solo numero de minutos; un aviso activo sin ninguno nunca eligio
     * antelacion y se lee como sin aviso, igual que el aviso general ({@link RecordatorioDelDia#delGeneral}).
     */
    private static RecordatorioDelDia recordatorioDe(HorarioPorFechaJpaEntity fila) {
        if (fila.getRecordatorioActivo() == null) {
            return null;
        }
        if (!fila.getRecordatorioActivo()) {
            return RecordatorioDelDia.sinAviso();
        }
        List<Integer> antelaciones = PreferenciaHorarioPersistenceMapper.aLista(fila.getAntelacionesRecordatorio());
        if (antelaciones != null && !antelaciones.isEmpty()) {
            return RecordatorioDelDia.con(antelaciones);
        }
        return fila.getMinutosRecordatorio() == null ? RecordatorioDelDia.sinAviso()
                : RecordatorioDelDia.con(List.of(fila.getMinutosRecordatorio().intValue()));
    }

    private static AjusteDeAlarma alarmaDe(HorarioPorFechaJpaEntity fila) {
        return PreferenciaHorarioPersistenceMapper.alarmaDe(fila.getAlarmaSonido(), fila.getAlarmaVibracion(), fila.getAlarmaVoz(),
                fila.getAlarmaRepetir());
    }

    @Override
    public void borrarParaFecha(UserId participanteId, HabitoId habitoId, LocalDate fecha) {
        var key = new HorarioPorFechaPk(participanteId.value(), habitoId.value(), fecha);
        // `existsById` antes de borrar: volver a encender algo que nunca se apago es una operacion
        // legitima y no tiene que reventar. Idempotente, igual que el DELETE de `habit-unlocks`.
        if (fechasRepository.existsById(key)) {
            fechasRepository.deleteById(key);
            fechasRepository.flush();
        }
    }

    @Override
    public List<HorarioSemanal> horarioSemanalDe(UserId participanteId, HabitoId habitoId) {
        return semanalRepository.findByParticipanteIdAndHabitoId(participanteId.value(), habitoId.value()).stream()
                .map(PreferenciaHorarioPersistenceAdapter::aHorarioSemanal)
                .toList();
    }

    @Override
    public List<HorarioSemanal> horariosSemanalesDe(UserId participanteId, Collection<HabitoId> habitoIds) {
        if (habitoIds.isEmpty()) {
            return List.of();
        }
        var ids = habitoIds.stream().map(HabitoId::value).toList();
        return semanalRepository.findByParticipanteIdAndHabitoIdIn(participanteId.value(), ids).stream()
                .map(PreferenciaHorarioPersistenceAdapter::aHorarioSemanal)
                .toList();
    }

    @Override
    public Set<HabitoId> habitosConEsquemaPorDia(UserId participanteId, Collection<HabitoId> habitoIds) {
        return horariosSemanalesDe(participanteId, habitoIds).stream()
                .map(h -> h.preferencia().habitoId())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void saveParaDiaSemana(UserId participanteId, HabitoId habitoId, HorarioSemanal horario) {
        var p = horario.preferencia();
        var key = new HorarioSemanalPk(participanteId.value(), habitoId.value(),
                (short) horario.diaSemana().getValue());
        var existente = semanalRepository.findById(key);
        var creadoEn = existente.map(HorarioSemanalJpaEntity::getCreadoEn).orElse(p.creadoEn());
        // FR-013 y spec 009: sin aviso o sin alarma en el agregado, lo propio que el dia ya tenia no se toca.
        RecordatorioDelDia aviso = horario.recordatorio() != null ? horario.recordatorio()
                : existente.map(PreferenciaHorarioPersistenceAdapter::recordatorioDe).orElse(null);
        AjusteDeAlarma alarma = horario.alarma() != null ? horario.alarma()
                : existente.map(PreferenciaHorarioPersistenceAdapter::alarmaDe).orElse(null);
        var fila = new HorarioSemanalJpaEntity(participanteId.value(), habitoId.value(),
                (short) horario.diaSemana().getValue(), p.horaDisparo(), horario.activo(), p.horaLimite(),
                creadoEn, p.actualizadoEn());
        ponerAviso(fila, aviso);
        ponerAlarma(fila, alarma);
        semanalRepository.saveAndFlush(fila);
    }

    @Override
    public void fijarRecordatorioParaDiaSemana(UserId participanteId, HabitoId habitoId, DayOfWeek diaSemana,
                                               RecordatorioDelDia recordatorio) {
        fijarRecordatorioParaDiaSemana(participanteId, habitoId, diaSemana, recordatorio, clock.now());
    }

    @Override
    public void fijarRecordatorioParaDiaSemana(UserId participanteId, HabitoId habitoId, DayOfWeek diaSemana,
                                               RecordatorioDelDia recordatorio, java.time.Instant ahora) {
        var key = new HorarioSemanalPk(participanteId.value(), habitoId.value(), (short) diaSemana.getValue());
        var existente = semanalRepository.findById(key);
        if (existente.isEmpty()) {
            if (recordatorio != null) {
                // Fila de solo aviso: sin hora propia, asi la hora de ese dia sigue a la general.
                var nueva = new HorarioSemanalJpaEntity(participanteId.value(), habitoId.value(),
                        (short) diaSemana.getValue(), null, true, null, ahora, ahora);
                ponerAviso(nueva, recordatorio);
                semanalRepository.saveAndFlush(nueva);
            }
            return;
        }
        var fila = existente.get();
        if (!HorarioSemanal.dejaAlgoPropio(fila.isActivo(), fila.getHoraDisparo(), recordatorio, alarmaDe(fila))) {
            semanalRepository.deleteById(key);
            semanalRepository.flush();
            return;
        }
        ponerAviso(fila, recordatorio);
        fila.setActualizadoEn(ahora);
        semanalRepository.saveAndFlush(fila);
    }

    @Override
    public void fijarAlarmaParaDiaSemana(UserId participanteId, HabitoId habitoId, DayOfWeek diaSemana,
                                         AjusteDeAlarma alarma) {
        fijarAlarmaParaDiaSemana(participanteId, habitoId, diaSemana, alarma, clock.now());
    }

    @Override
    public void fijarAlarmaParaDiaSemana(UserId participanteId, HabitoId habitoId, DayOfWeek diaSemana,
                                         AjusteDeAlarma alarma, java.time.Instant ahora) {
        var key = new HorarioSemanalPk(participanteId.value(), habitoId.value(), (short) diaSemana.getValue());
        var existente = semanalRepository.findById(key);
        AjusteDeAlarma propia = alarma == null || alarma.esVacio() ? null : alarma;
        if (existente.isEmpty()) {
            if (propia != null) {
                // Fila de solo alarma: sin hora ni aviso propios, asi siguen a los generales.
                var nueva = new HorarioSemanalJpaEntity(participanteId.value(), habitoId.value(),
                        (short) diaSemana.getValue(), null, true, null, ahora, ahora);
                ponerAlarma(nueva, propia);
                semanalRepository.saveAndFlush(nueva);
            }
            return;
        }
        var fila = existente.get();
        if (!HorarioSemanal.dejaAlgoPropio(fila.isActivo(), fila.getHoraDisparo(), recordatorioDe(fila), propia)) {
            semanalRepository.deleteById(key);
            semanalRepository.flush();
            return;
        }
        ponerAlarma(fila, propia);
        fila.setActualizadoEn(ahora);
        semanalRepository.saveAndFlush(fila);
    }

    private static void ponerAviso(HorarioSemanalJpaEntity fila, RecordatorioDelDia aviso) {
        fila.setRecordatorioActivo(activoDe(aviso));
        fila.setAntelacionesRecordatorio(antelacionesDe(aviso));
    }

    /** Las cuatro columnas de la combinacion (V112); {@code null} deja las cuatro en NULL: hereda. */
    private static void ponerAlarma(HorarioSemanalJpaEntity fila, AjusteDeAlarma alarma) {
        fila.setAlarmaSonido(alarma == null ? null : alarma.sonido());
        fila.setAlarmaVibracion(alarma == null ? null : alarma.vibracion());
        fila.setAlarmaVoz(alarma == null ? null : alarma.voz());
        fila.setAlarmaRepetir(alarma == null ? null : alarma.repetir());
    }

    private static Boolean activoDe(RecordatorioDelDia recordatorio) {
        return recordatorio == null ? null : recordatorio.activo();
    }

    /** Solo un aviso ACTIVO guarda antelaciones (el CHECK de V111). */
    private static Short[] antelacionesDe(RecordatorioDelDia recordatorio) {
        return recordatorio == null || !recordatorio.activo() ? null
                : PreferenciaHorarioPersistenceMapper.aArreglo(recordatorio.antelaciones());
    }

    @Override
    public List<HabitoId> habitosApagadosEnDiaSemana(UserId participanteId, DayOfWeek diaSemana) {
        return semanalRepository.apagadosEnDiaSemana(participanteId.value(), (short) diaSemana.getValue())
                .stream().map(HabitoId::of).toList();
    }

    @Override
    public void borrarParaDiaSemana(UserId participanteId, HabitoId habitoId, DayOfWeek diaSemana) {
        var key = new HorarioSemanalPk(participanteId.value(), habitoId.value(), (short) diaSemana.getValue());
        // `existsById` antes de borrar: quitar una hora que nunca se puso es legitimo y no tiene
        // que reventar. Idempotente, igual que el resto de los borrados de este modulo.
        if (semanalRepository.existsById(key)) {
            semanalRepository.deleteById(key);
            semanalRepository.flush();
        }
    }

    @Override
    public List<HabitoId> habitosApagadosEn(UserId participanteId, LocalDate fecha) {
        return fechasRepository.apagadosEn(participanteId.value(), fecha).stream().map(HabitoId::of).toList();
    }

    @Override
    public List<HabitoId> habitosConHorarioEntre(UserId participanteId, LocalDate desde, LocalDate hasta) {
        return fechasRepository.habitosEntre(participanteId.value(), desde, hasta).stream().map(HabitoId::of).toList();
    }
}
