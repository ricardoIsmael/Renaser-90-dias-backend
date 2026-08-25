package com.renaser.os.users.application.ports.out.mentorprofile;

import com.renaser.os.users.domain.model.mentorprofile.MentorProfile;

public interface SaveMentorProfilePort {

    MentorProfile save(MentorProfile mentorProfile);
}
