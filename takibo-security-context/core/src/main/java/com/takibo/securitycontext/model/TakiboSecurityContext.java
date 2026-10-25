package com.takibo.securitycontext.model;

import com.takibo.securitycontext.exception.InvalidTakiboSecurityContextException;

import java.util.Map;
import java.util.Objects;

public final class TakiboSecurityContext {

    private final SubjectIdentity subject;
    private final TenantScope tenant;
    private final TransportContext transport;
    private final ClientContext client;
    private final TemporalContext temporal;
    private final ContextAttributeStore attributes;

    private TakiboSecurityContext(Builder builder) {
        this.subject = Objects.requireNonNull(builder.subject, "subject required");
        this.tenant = Objects.requireNonNull(builder.tenant, "tenant required");
        this.temporal = Objects.requireNonNull(builder.temporal, "temporal required");
        this.transport = builder.transport;
        this.client = builder.client;
        this.attributes = builder.attributes != null ? builder.attributes : new ContextAttributeStore(Map.of());
    }

    public SubjectIdentity subject() { return subject; }
    public TenantScope tenant() { return tenant; }
    public TransportContext transport() { return transport; }
    public ClientContext client() { return client; }
    public TemporalContext temporal() { return temporal; }
    public ContextAttributeStore attributes() { return attributes; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private SubjectIdentity subject;
        private TenantScope tenant;
        private TransportContext transport;
        private ClientContext client;
        private TemporalContext temporal;
        private ContextAttributeStore attributes;

        public Builder subject(SubjectIdentity subject) {
            this.subject = subject;
            return this;
        }

        public Builder tenant(TenantScope tenant) {
            this.tenant = tenant;
            return this;
        }

        public Builder transport(TransportContext transport) {
            this.transport = transport;
            return this;
        }

        public Builder client(ClientContext client) {
            this.client = client;
            return this;
        }

        public Builder temporal(TemporalContext temporal) {
            this.temporal = temporal;
            return this;
        }

        public Builder attributes(ContextAttributeStore attributes) {
            this.attributes = attributes;
            return this;
        }

        public TakiboSecurityContext build() {
            if (subject == null) throw new InvalidTakiboSecurityContextException("subject required");
            if (tenant == null) throw new InvalidTakiboSecurityContextException("tenant required");
            if (temporal == null) throw new InvalidTakiboSecurityContextException("temporal required");
            return new TakiboSecurityContext(this);
        }
    }
}
