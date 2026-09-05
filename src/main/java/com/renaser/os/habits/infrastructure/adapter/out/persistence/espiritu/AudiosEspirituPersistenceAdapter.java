package com.renaser.os.habits.infrastructure.adapter.out.persistence.espiritu;

import com.renaser.os.habits.application.ports.out.espiritu.AudioCatalogPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Lee el catalogo de "Pastilla Renacer" directo de {@code audios_espiritu} — la tabla propia,
 * con sus 43 filas reales cargadas por {@code V5__guias_audios_habitos_default.sql}.
 *
 * <p><b>Por que existe (y por que reemplaza al NoOp como default).</b> El catalogo nacio
 * detras de un puerto pensando en sincronizarlo desde Google Drive, y mientras eso no
 * existiera el unico adapter era {@code NoOpAudioCatalogAdapter}: siempre vacio, asi que
 * {@code EspirituService.asegurarAvance} nunca llegaba a crear un track y el habito quedaba
 * mudo. Pero D-48 ya dejo constancia de que ese estado era un cabo suelto, no un diseno:
 * "con los 43 audios_espiritu ya cargados en V5, el catalogo tiene datos pero el adapter los
 * ignora a proposito ... hasta que se conecte un adapter real (Drive o uno que lea directo de
 * audios_espiritu, ya que la tabla existe)". Esta clase es esa segunda opcion. No integra
 * Drive: la decision D-34 sigue en pie, simplemente ya no hace falta Drive para saber QUE
 * audio le toca a cada dia.
 *
 * <p><b>Cambio de comportamiento observable, a proposito:</b> con este adapter activo un
 * aprendiz que llega al dia de programa 8 SI recibe su primer track de Espiritu (audio 1),
 * cosa que antes no pasaba nunca. Es exactamente lo que el dueno del producto pidio. Para
 * volver al comportamiento anterior sin tocar codigo: {@code renaser.espiritu.catalogo=noop}.
 *
 * <p><b>Lo que este adapter NO resuelve:</b> el audio REPRODUCIBLE. Devuelve
 * {@code rutaStorage} tal como esta en la tabla, y hoy esta en NULL en las 43 filas porque
 * los mp3 nunca se migraron del Drive viejo al bucket (V25 y D-50). Titulo, dia y estado
 * funcionan; la URL de reproduccion queda en {@code null} hasta que se haga esa migracion.
 */
@Component
@ConditionalOnProperty(name = "renaser.espiritu.catalogo", havingValue = "bd", matchIfMissing = true)
class AudiosEspirituPersistenceAdapter implements AudioCatalogPort {

    private final SpringDataAudiosEspirituRepository repository;

    AudiosEspirituPersistenceAdapter(SpringDataAudiosEspirituRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AudioEspiritu> porDia(int dia) {
        return repository.findById(dia).map(AudiosEspirituPersistenceAdapter::toDomain);
    }

    @Override
    public List<AudioEspiritu> todos() {
        return repository.findAllByOrderByDiaAsc().stream().map(AudiosEspirituPersistenceAdapter::toDomain).toList();
    }

    private static AudioEspiritu toDomain(AudioEspirituJpaEntity e) {
        return new AudioEspiritu(e.getDia(), e.getTitulo(), e.getDriveFileId(), e.getMime(), e.getTamanoBytes(),
                e.getRutaStorage());
    }
}
