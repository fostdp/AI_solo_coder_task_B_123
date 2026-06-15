CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

CREATE TABLE nutrition_sensor_data_partitioned (
    id BIGSERIAL,
    sensor_id VARCHAR(50),
    barracks_code VARCHAR(50),
    soldier_code VARCHAR(50),
    protein_g DECIMAL(10,2),
    fat_g DECIMAL(10,2),
    vitamin_c_mg DECIMAL(10,2),
    calorie_kcal DECIMAL(10,2),
    recorded_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, recorded_at)
) PARTITION BY RANGE (recorded_at);

CREATE TABLE nutrition_sensor_data_2024_01 PARTITION OF nutrition_sensor_data_partitioned
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
CREATE TABLE nutrition_sensor_data_2024_02 PARTITION OF nutrition_sensor_data_partitioned
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
CREATE TABLE nutrition_sensor_data_2024_03 PARTITION OF nutrition_sensor_data_partitioned
    FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');
CREATE TABLE nutrition_sensor_data_2024_04 PARTITION OF nutrition_sensor_data_partitioned
    FOR VALUES FROM ('2024-04-01') TO ('2024-05-01');
CREATE TABLE nutrition_sensor_data_2024_05 PARTITION OF nutrition_sensor_data_partitioned
    FOR VALUES FROM ('2024-05-01') TO ('2024-06-01');
CREATE TABLE nutrition_sensor_data_2024_06 PARTITION OF nutrition_sensor_data_partitioned
    FOR VALUES FROM ('2024-06-01') TO ('2024-07-01');
CREATE TABLE nutrition_sensor_data_2024_07 PARTITION OF nutrition_sensor_data_partitioned
    FOR VALUES FROM ('2024-07-01') TO ('2024-08-01');
CREATE TABLE nutrition_sensor_data_2024_08 PARTITION OF nutrition_sensor_data_partitioned
    FOR VALUES FROM ('2024-08-01') TO ('2024-09-01');
CREATE TABLE nutrition_sensor_data_2024_09 PARTITION OF nutrition_sensor_data_partitioned
    FOR VALUES FROM ('2024-09-01') TO ('2024-10-01');
CREATE TABLE nutrition_sensor_data_2024_10 PARTITION OF nutrition_sensor_data_partitioned
    FOR VALUES FROM ('2024-10-01') TO ('2024-11-01');
CREATE TABLE nutrition_sensor_data_2024_11 PARTITION OF nutrition_sensor_data_partitioned
    FOR VALUES FROM ('2024-11-01') TO ('2024-12-01');
CREATE TABLE nutrition_sensor_data_2024_12 PARTITION OF nutrition_sensor_data_partitioned
    FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');

CREATE INDEX idx_nutrition_sensor_recorded ON nutrition_sensor_data_partitioned (sensor_id, recorded_at);
CREATE INDEX idx_nutrition_barracks ON nutrition_sensor_data_partitioned (barracks_code);

CREATE TABLE fecal_sensor_data_partitioned (
    id BIGSERIAL,
    sensor_id VARCHAR(50),
    barracks_code VARCHAR(50),
    soldier_code VARCHAR(50),
    pathogen_type VARCHAR(100),
    concentration DECIMAL(12,6),
    unit VARCHAR(20),
    recorded_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, recorded_at)
) PARTITION BY RANGE (recorded_at);

CREATE TABLE fecal_sensor_data_2024_01 PARTITION OF fecal_sensor_data_partitioned
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
CREATE TABLE fecal_sensor_data_2024_02 PARTITION OF fecal_sensor_data_partitioned
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
CREATE TABLE fecal_sensor_data_2024_03 PARTITION OF fecal_sensor_data_partitioned
    FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');
CREATE TABLE fecal_sensor_data_2024_04 PARTITION OF fecal_sensor_data_partitioned
    FOR VALUES FROM ('2024-04-01') TO ('2024-05-01');
CREATE TABLE fecal_sensor_data_2024_05 PARTITION OF fecal_sensor_data_partitioned
    FOR VALUES FROM ('2024-05-01') TO ('2024-06-01');
CREATE TABLE fecal_sensor_data_2024_06 PARTITION OF fecal_sensor_data_partitioned
    FOR VALUES FROM ('2024-06-01') TO ('2024-07-01');
CREATE TABLE fecal_sensor_data_2024_07 PARTITION OF fecal_sensor_data_partitioned
    FOR VALUES FROM ('2024-07-01') TO ('2024-08-01');
CREATE TABLE fecal_sensor_data_2024_08 PARTITION OF fecal_sensor_data_partitioned
    FOR VALUES FROM ('2024-08-01') TO ('2024-09-01');
CREATE TABLE fecal_sensor_data_2024_09 PARTITION OF fecal_sensor_data_partitioned
    FOR VALUES FROM ('2024-09-01') TO ('2024-10-01');
CREATE TABLE fecal_sensor_data_2024_10 PARTITION OF fecal_sensor_data_partitioned
    FOR VALUES FROM ('2024-10-01') TO ('2024-11-01');
CREATE TABLE fecal_sensor_data_2024_11 PARTITION OF fecal_sensor_data_partitioned
    FOR VALUES FROM ('2024-11-01') TO ('2024-12-01');
CREATE TABLE fecal_sensor_data_2024_12 PARTITION OF fecal_sensor_data_partitioned
    FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');

CREATE INDEX idx_fecal_sensor_recorded ON fecal_sensor_data_partitioned (sensor_id, recorded_at);
CREATE INDEX idx_fecal_barracks ON fecal_sensor_data_partitioned (barracks_code);
