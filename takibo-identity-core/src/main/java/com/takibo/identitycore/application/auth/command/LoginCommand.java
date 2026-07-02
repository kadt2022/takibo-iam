package com.takibo.identitycore.application.auth.command;

/** Intention de login humain : credentials + frontière demandée (codes lisibles). */
public record LoginCommand(
        String email,
        String password,
        String orgCode,
        String spaceCode
) {
}
