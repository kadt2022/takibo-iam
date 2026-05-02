package com.takibo.managementservice.domain.model;

public record RegisteredClientResult(OAuthClient client, String oneTimePlainSecret) {}