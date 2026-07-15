package com.takibo.identitycore.application.spacecontext.port.in;

import com.takibo.identitycore.interfaces.rest.response.CurrentUserSpacesResponse;

public interface CurrentUserSpaceQueryCase {

    CurrentUserSpacesResponse listAccessibleSpaces();
}
