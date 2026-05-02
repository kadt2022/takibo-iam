-- ADP User Behavior Profiles
-- Compatible MySQL + PostgreSQL
-- Convention: collection join column = user_behavior_profile_id

CREATE TABLE adp_user_profiles (
    id                               VARCHAR(36) PRIMARY KEY,
    user_id                          VARCHAR(255) NOT NULL UNIQUE,
    created_at                       TIMESTAMP NULL,
    updated_at                       TIMESTAMP NULL,
    last_location                    VARCHAR(255),
    last_location_lat                DOUBLE PRECISION,
    last_location_lon                DOUBLE PRECISION,
    last_access_time                 TIMESTAMP NULL,
    primary_device_type              VARCHAR(50),
    average_requests_per_session     INT,
    average_session_duration_minutes INT,
    total_access_count               BIGINT,
    failed_attempts_last_24h         INT,
    last_password_change             TIMESTAMP NULL,
    mfa_enabled                      BOOLEAN DEFAULT FALSE
);

CREATE TABLE adp_profile_browsers (
    user_behavior_profile_id VARCHAR(36) NOT NULL,
    browser                  VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_behavior_profile_id, browser),
    CONSTRAINT fk_adp_browsers_profile
        FOREIGN KEY (user_behavior_profile_id) REFERENCES adp_user_profiles(id)
        ON DELETE CASCADE
);

CREATE TABLE adp_profile_countries (
    user_behavior_profile_id VARCHAR(36) NOT NULL,
    country                  VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_behavior_profile_id, country),
    CONSTRAINT fk_adp_countries_profile
        FOREIGN KEY (user_behavior_profile_id) REFERENCES adp_user_profiles(id)
        ON DELETE CASCADE
);

CREATE TABLE adp_profile_cities (
    user_behavior_profile_id VARCHAR(36) NOT NULL,
    city                     VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_behavior_profile_id, city),
    CONSTRAINT fk_adp_cities_profile
        FOREIGN KEY (user_behavior_profile_id) REFERENCES adp_user_profiles(id)
        ON DELETE CASCADE
);

CREATE TABLE adp_profile_devices (
    user_behavior_profile_id VARCHAR(36) NOT NULL,
    device_id                VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_behavior_profile_id, device_id),
    CONSTRAINT fk_adp_devices_profile
        FOREIGN KEY (user_behavior_profile_id) REFERENCES adp_user_profiles(id)
        ON DELETE CASCADE
);

CREATE TABLE adp_profile_os (
    user_behavior_profile_id VARCHAR(36) NOT NULL,
    os                       VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_behavior_profile_id, os),
    CONSTRAINT fk_adp_os_profile
        FOREIGN KEY (user_behavior_profile_id) REFERENCES adp_user_profiles(id)
        ON DELETE CASCADE
);

CREATE TABLE adp_profile_ips (
    user_behavior_profile_id VARCHAR(36) NOT NULL,
    ip_address               VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_behavior_profile_id, ip_address),
    CONSTRAINT fk_adp_ips_profile
        FOREIGN KEY (user_behavior_profile_id) REFERENCES adp_user_profiles(id)
        ON DELETE CASCADE
);

CREATE TABLE adp_profile_isps (
    user_behavior_profile_id VARCHAR(36) NOT NULL,
    isp                      VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_behavior_profile_id, isp),
    CONSTRAINT fk_adp_isps_profile
        FOREIGN KEY (user_behavior_profile_id) REFERENCES adp_user_profiles(id)
        ON DELETE CASCADE
);

CREATE TABLE adp_profile_hours (
    user_behavior_profile_id VARCHAR(36) NOT NULL,
    hour                     INT NOT NULL,
    PRIMARY KEY (user_behavior_profile_id, hour),
    CONSTRAINT fk_adp_hours_profile
        FOREIGN KEY (user_behavior_profile_id) REFERENCES adp_user_profiles(id)
        ON DELETE CASCADE
);

CREATE TABLE adp_profile_days (
    user_behavior_profile_id VARCHAR(36) NOT NULL,
    day_of_week              INT NOT NULL,
    PRIMARY KEY (user_behavior_profile_id, day_of_week),
    CONSTRAINT fk_adp_days_profile
        FOREIGN KEY (user_behavior_profile_id) REFERENCES adp_user_profiles(id)
        ON DELETE CASCADE
);

CREATE TABLE adp_profile_resources (
    user_behavior_profile_id VARCHAR(36) NOT NULL,
    resource                 VARCHAR(500) NOT NULL,
    PRIMARY KEY (user_behavior_profile_id, resource),
    CONSTRAINT fk_adp_resources_profile
        FOREIGN KEY (user_behavior_profile_id) REFERENCES adp_user_profiles(id)
        ON DELETE CASCADE
);

CREATE TABLE adp_profile_sequences (
    user_behavior_profile_id VARCHAR(36) NOT NULL,
    sequence                 VARCHAR(500) NOT NULL,
    PRIMARY KEY (user_behavior_profile_id, sequence),
    CONSTRAINT fk_adp_sequences_profile
        FOREIGN KEY (user_behavior_profile_id) REFERENCES adp_user_profiles(id)
        ON DELETE CASCADE
);

-- Indexes utiles (FK lookup rapide) - optionnel mais recommandé
CREATE INDEX idx_adp_user_id ON adp_user_profiles(user_id);
CREATE INDEX idx_adp_updated_at ON adp_user_profiles(updated_at);
CREATE INDEX idx_adp_profile_browsers_profile  ON adp_profile_browsers(user_behavior_profile_id);
CREATE INDEX idx_adp_profile_countries_profile ON adp_profile_countries(user_behavior_profile_id);
CREATE INDEX idx_adp_profile_cities_profile    ON adp_profile_cities(user_behavior_profile_id);
CREATE INDEX idx_adp_profile_devices_profile   ON adp_profile_devices(user_behavior_profile_id);
CREATE INDEX idx_adp_profile_os_profile        ON adp_profile_os(user_behavior_profile_id);
CREATE INDEX idx_adp_profile_ips_profile       ON adp_profile_ips(user_behavior_profile_id);
CREATE INDEX idx_adp_profile_isps_profile      ON adp_profile_isps(user_behavior_profile_id);
CREATE INDEX idx_adp_profile_hours_profile     ON adp_profile_hours(user_behavior_profile_id);
CREATE INDEX idx_adp_profile_days_profile      ON adp_profile_days(user_behavior_profile_id);
CREATE INDEX idx_adp_profile_resources_profile ON adp_profile_resources(user_behavior_profile_id);
CREATE INDEX idx_adp_profile_sequences_profile ON adp_profile_sequences(user_behavior_profile_id);