package com.renaser.os.users.application.ports.in.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.user.User;

public interface GetMyProfileUseCase {

    User getMyProfile(UserId userId);
}
