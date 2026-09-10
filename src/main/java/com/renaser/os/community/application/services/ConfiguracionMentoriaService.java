package com.renaser.os.community.application.services;

import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import com.renaser.os.community.application.ports.in.acompanamiento.ConfigurarMentoriaUseCase;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SavePoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.CadenciaRotacion;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Configuración operativa de una cohorte.
 *
 * <p>Dos reglas gobiernan todo el archivo. La primera: <b>se valida la lista entera antes de
 * escribir nada</b>. Reemplazar guía por guía dejaría la recepción a medias cuando el tercer
 * correo está mal escrito, y el administrador tendría que adivinar cuáles entraron. La segunda:
 * <b>nada de esto crea usuarios ni cambia roles</b> — designar una función es distinto de dar
 * de alta una cuenta.
 */
@Service
public class ConfiguracionMentoriaService implements ConfigurarMentoriaUseCase {

    private final LoadPoliticaMentoriaPort loadPoliticaPort;
    private final SavePoliticaMentoriaPort savePoliticaPort;
    private final LoadAsignacionesPort loadAsignacionesPort;
    private final SaveAsignacionPort saveAsignacionPort;
    private final LoadCelulaPort loadCelulaPort;
    private final UserSummaryFinder userSummaryFinder;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public ConfiguracionMentoriaService(LoadPoliticaMentoriaPort loadPoliticaPort,
                                         SavePoliticaMentoriaPort savePoliticaPort,
                                         LoadAsignacionesPort loadAsignacionesPort,
                                         SaveAsignacionPort saveAsignacionPort, LoadCelulaPort loadCelulaPort,
                                         UserSummaryFinder userSummaryFinder,
                                         ApplicationEventPublisher eventos, Clock clock,
                                         IdGenerator idGenerator) {
        this.loadPoliticaPort = loadPoliticaPort;
        this.savePoliticaPort = savePoliticaPort;
        this.loadAsignacionesPort = loadAsignacionesPort;
        this.saveAsignacionPort = saveAsignacionPort;
        this.loadCelulaPort = loadCelulaPort;
        this.userSummaryFinder = userSummaryFinder;
        this.eventos = eventos;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional(readOnly = true)
    public PoliticaConfigurada consultar(UserId actorId, CohorteId cohorteId) {
        return aRespuesta(politicaDe(cohorteId));
    }

    @Override
    @Transactional
    public PoliticaConfigurada reconfigurar(ReconfigurarPolitica comando) {
        PoliticaMentoria politica = politicaDe(comando.cohorteId());
        // reconfigurar() valida rangos y zona, y rechaza si la version no coincide.
        politica.reconfigurar(comando.capacidadCelula(), cadencia(comando.cadenciaRotacion()),
                comando.zonaHoraria(), comando.diaTraslado(), comando.diasSinActividadAlerta(),
                comando.versionEsperada());
        return aRespuesta(savePoliticaPort.save(politica));
    }

    /**
     * Reemplazo atómico de la lista de guías: cierra las designaciones que ya no están y abre las
     * que faltan. Repetir la misma lista no cambia nada ni abre otro intervalo.
     */
    @Override
    @Transactional
    public GuiasConfigurados reemplazarGuias(ReemplazarGuias comando) {
        PoliticaMentoria politica = politicaDe(comando.cohorteId());

        CelulaId recepcion = comando.celulaRecepcionId() != null
                ? CelulaId.of(comando.celulaRecepcionId())
                : politica.celulaRecepcionId();
        if (recepcion == null) {
            throw new IllegalArgumentException(
                    "La cohorte no tiene celula de recepcion designada: indica cual antes de asignar guias");
        }
        Celula celula = loadCelulaPort.porId(recepcion)
                .orElseThrow(() -> new NoSuchElementException("Celula de recepcion no encontrada: " + recepcion));
        if (!celula.cohorteId().equals(comando.cohorteId())) {
            throw new IllegalArgumentException("Esa celula no pertenece a la cohorte indicada");
        }

        // TODA la lista primero. Si una referencia falla, no se escribio nada todavia.
        Set<UserId> deseados = resolverTodos(comando.referencias());

        Instant ahora = clock.now();
        List<AsignacionCelula> vigentes = loadAsignacionesPort.porCelula(recepcion).stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.GUIA)
                .filter(a -> a.vigenteEn(ahora))
                .toList();

        for (AsignacionCelula sobrante : vigentes) {
            if (!deseados.contains(sobrante.usuarioId())) {
                sobrante.cerrar(ahora, MotivoAsignacion.ADMINISTRATIVO);
                saveAsignacionPort.save(sobrante);
            }
        }
        Set<UserId> yaEstaban = vigentes.stream().map(AsignacionCelula::usuarioId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (UserId nuevo : deseados) {
            if (!yaEstaban.contains(nuevo)) {
                saveAsignacionPort.save(AsignacionCelula.abrir(AsignacionId.of(idGenerator.newId()), recepcion,
                        nuevo, FuncionAcompanamiento.GUIA, ahora, MotivoAsignacion.ADMINISTRATIVO,
                        comando.actorId(), "guias:" + recepcion.value() + ":" + nuevo.value() + ":" + ahora));
            }
        }

        if (!recepcion.equals(politica.celulaRecepcionId())) {
            politica.designarRecepcion(recepcion);
            savePoliticaPort.save(politica);
        }
        // El chat de recepcion tiene que reflejar quien atiende ahora.
        eventos.publishEvent(new ComposicionDeCelulaCambiadaEvent(recepcion.value(), ahora));

        return new GuiasConfigurados(comando.cohorteId().value(), recepcion.value(),
                deseados.stream().map(UserId::value).toList());
    }

    /**
     * Resuelve cada referencia y rechaza la operación completa ante la primera que falla. Un
     * usuario suspendido no puede atender la recepción: designarlo dejaría la cohorte creyendo
     * que tiene cobertura cuando no la tiene.
     */
    private Set<UserId> resolverTodos(List<ReferenciaDeUsuario> referencias) {
        Set<UserId> resueltos = new LinkedHashSet<>();
        List<String> problemas = new ArrayList<>();

        for (ReferenciaDeUsuario referencia : referencias) {
            boolean porId = referencia.userId() != null;
            boolean porEmail = referencia.email() != null && !referencia.email().isBlank();
            if (porId == porEmail) {
                problemas.add("Cada guia se nombra por userId O por email, no por ambos ni por ninguno");
                continue;
            }
            Optional<UserSummary> encontrado = porId
                    ? userSummaryFinder.findById(UserId.of(referencia.userId()))
                    : userSummaryFinder.findByEmail(referencia.email());
            if (encontrado.isEmpty()) {
                problemas.add("No existe el usuario " + (porId ? referencia.userId() : referencia.email()));
                continue;
            }
            if (encontrado.get().status() != UserStatus.ACTIVE) {
                problemas.add("La cuenta de " + encontrado.get().fullName() + " no esta activa");
                continue;
            }
            // Repetir a la misma persona no es un error: la lista se deduplica y queda una vez.
            resueltos.add(encontrado.get().id());
        }

        if (!problemas.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problemas));
        }
        return resueltos;
    }

    private static CadenciaRotacion cadencia(String valor) {
        try {
            return CadenciaRotacion.valueOf(valor);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Cadencia invalida: " + valor + ". Usa MENSUAL o SEMANAL");
        }
    }

    private PoliticaMentoria politicaDe(CohorteId cohorteId) {
        return loadPoliticaPort.porCohorte(cohorteId).orElseGet(() -> PoliticaMentoria.porDefecto(cohorteId));
    }

    private static PoliticaConfigurada aRespuesta(PoliticaMentoria politica) {
        return new PoliticaConfigurada(politica.cohorteId().value(), politica.capacidadCelula(),
                politica.cadenciaRotacion().name(), politica.zonaHoraria(), politica.diaTraslado(),
                politica.diasSinActividadAlerta(),
                politica.celulaRecepcionId() == null ? null : politica.celulaRecepcionId().value(),
                politica.version());
    }
}
