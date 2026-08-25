package com.renaser.os.onboarding.application.ports.out.media;

import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;

public interface SaveMediaPort {

    MediaOnboarding guardar(MediaOnboarding media);
}
