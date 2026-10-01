CREATE TABLE rides
(
    id                  VARCHAR(36)   NOT NULL,
    version             BIGINT        NOT NULL,
    rider_id            VARCHAR(64)   NOT NULL,
    driver_id           VARCHAR(64)   NULL,
    pickup_latitude     DOUBLE        NOT NULL,
    pickup_longitude    DOUBLE        NOT NULL,
    pickup_address      VARCHAR(255)  NOT NULL,
    drop_latitude       DOUBLE        NOT NULL,
    drop_longitude      DOUBLE        NOT NULL,
    drop_address        VARCHAR(255)  NOT NULL,
    distance_km         DOUBLE        NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    estimated_fare      DECIMAL(10, 2) NOT NULL,
    actual_fare         DECIMAL(10, 2) NULL,
    cancellation_reason VARCHAR(255)  NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    accepted_at         DATETIME(6)   NULL,
    started_at          DATETIME(6)   NULL,
    completed_at        DATETIME(6)   NULL,
    cancelled_at        DATETIME(6)   NULL,
    PRIMARY KEY (id),
    INDEX idx_rides_rider_created (rider_id, created_at),
    INDEX idx_rides_driver_created (driver_id, created_at),
    INDEX idx_rides_status_updated (status, updated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
