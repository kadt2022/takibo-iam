package com.takibo.managementservice.application.port;



import com.takibo.managementservice.application.security.ActorSource;

import java.util.UUID;

public interface CurrentActorProvider {
    UUID currentUserId();
    ActorSource source();
}
