-- 古代兵营士兵膳食营养监测与疫病预警系统 - 数据库初始化脚本

-- 启用PostGIS扩展
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- 创建数据库
-- CREATE DATABASE barracks_monitor;

-- 兵营表
CREATE TABLE IF NOT EXISTS barracks (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    location GEOMETRY(Point, 4326) NOT NULL,
    description TEXT,
    capacity INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_barracks_location ON barracks USING GIST(location);

-- 士兵表
CREATE TABLE IF NOT EXISTS soldier (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    soldier_code VARCHAR(50) NOT NULL UNIQUE,
    barracks_id BIGINT NOT NULL REFERENCES barracks(id),
    age INTEGER NOT NULL,
    rank VARCHAR(50),
    origin_region VARCHAR(20) NOT NULL DEFAULT 'NORTH',
    position GEOMETRY(Point, 4326) NOT NULL,
    position_x INTEGER NOT NULL,
    position_y INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'HEALTHY',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_soldier_barracks ON soldier(barracks_id);
CREATE INDEX IF NOT EXISTS idx_soldier_position ON soldier USING GIST(position);

-- 膳食记录表
CREATE TABLE IF NOT EXISTS meal_record (
    id BIGSERIAL PRIMARY KEY,
    soldier_id BIGINT NOT NULL REFERENCES soldier(id),
    meal_type VARCHAR(20) NOT NULL,
    meal_time TIMESTAMP NOT NULL,
    protein_g DECIMAL(10,2) NOT NULL DEFAULT 0,
    fat_g DECIMAL(10,2) NOT NULL DEFAULT 0,
    vitamin_c_mg DECIMAL(10,2) NOT NULL DEFAULT 0,
    calorie_kcal DECIMAL(10,2) NOT NULL DEFAULT 0,
    food_items TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_meal_record_soldier ON meal_record(soldier_id);
CREATE INDEX IF NOT EXISTS idx_meal_record_time ON meal_record(meal_time);

-- 体能消耗记录表
CREATE TABLE IF NOT EXISTS physical_activity (
    id BIGSERIAL PRIMARY KEY,
    soldier_id BIGINT NOT NULL REFERENCES soldier(id),
    activity_date DATE NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    duration_minutes INTEGER NOT NULL DEFAULT 0,
    calorie_burned DECIMAL(10,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_physical_activity_soldier ON physical_activity(soldier_id);
CREATE INDEX IF NOT EXISTS idx_physical_activity_date ON physical_activity(activity_date);

-- 营养分析仪数据表
CREATE TABLE IF NOT EXISTS nutrition_sensor_data (
    id BIGSERIAL PRIMARY KEY,
    sensor_id VARCHAR(50) NOT NULL,
    barracks_id BIGINT NOT NULL REFERENCES barracks(id),
    soldier_id BIGINT REFERENCES soldier(id),
    protein_g DECIMAL(10,2) NOT NULL DEFAULT 0,
    fat_g DECIMAL(10,2) NOT NULL DEFAULT 0,
    vitamin_c_mg DECIMAL(10,2) NOT NULL DEFAULT 0,
    sample_time TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_nutrition_sensor_barracks ON nutrition_sensor_data(barracks_id);
CREATE INDEX IF NOT EXISTS idx_nutrition_sensor_time ON nutrition_sensor_data(sample_time);
CREATE INDEX IF NOT EXISTS idx_nutrition_sensor_soldier ON nutrition_sensor_data(soldier_id);

-- 粪便隐血传感器数据表
CREATE TABLE IF NOT EXISTS fecal_sensor_data (
    id BIGSERIAL PRIMARY KEY,
    sensor_id VARCHAR(50) NOT NULL,
    barracks_id BIGINT NOT NULL REFERENCES barracks(id),
    soldier_id BIGINT REFERENCES soldier(id),
    is_positive BOOLEAN NOT NULL DEFAULT FALSE,
    sample_time TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fecal_sensor_barracks ON fecal_sensor_data(barracks_id);
CREATE INDEX IF NOT EXISTS idx_fecal_sensor_time ON fecal_sensor_data(sample_time);
CREATE INDEX IF NOT EXISTS idx_fecal_sensor_soldier ON fecal_sensor_data(soldier_id);

-- 营养风险预测表
CREATE TABLE IF NOT EXISTS nutrition_risk (
    id BIGSERIAL PRIMARY KEY,
    soldier_id BIGINT NOT NULL REFERENCES soldier(id),
    risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW',
    protein_risk_score DECIMAL(5,4) NOT NULL DEFAULT 0,
    fat_risk_score DECIMAL(5,4) NOT NULL DEFAULT 0,
    vitamin_c_risk_score DECIMAL(5,4) NOT NULL DEFAULT 0,
    overall_risk_score DECIMAL(5,4) NOT NULL DEFAULT 0,
    dietary_suggestion TEXT,
    predicted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_current BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_nutrition_risk_soldier ON nutrition_risk(soldier_id);
CREATE INDEX IF NOT EXISTS idx_nutrition_risk_level ON nutrition_risk(risk_level);

-- 传染病预警表
CREATE TABLE IF NOT EXISTS epidemic_alert (
    id BIGSERIAL PRIMARY KEY,
    barracks_id BIGINT NOT NULL REFERENCES barracks(id),
    alert_type VARCHAR(50) NOT NULL,
    alert_level VARCHAR(20) NOT NULL,
    positive_rate DECIMAL(5,4) NOT NULL DEFAULT 0,
    affected_count INTEGER NOT NULL DEFAULT 0,
    total_count INTEGER NOT NULL DEFAULT 0,
    cluster_center GEOMETRY(Point, 4326),
    cluster_radius DECIMAL(10,2),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_epidemic_alert_barracks ON epidemic_alert(barracks_id);
CREATE INDEX IF NOT EXISTS idx_epidemic_alert_status ON epidemic_alert(status);

-- 告警推送记录表
CREATE TABLE IF NOT EXISTS notification_log (
    id BIGSERIAL PRIMARY KEY,
    alert_id BIGINT REFERENCES epidemic_alert(id),
    nutrition_risk_id BIGINT REFERENCES nutrition_risk(id),
    notification_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    recipient VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_notification_log_type ON notification_log(notification_type);
CREATE INDEX IF NOT EXISTS idx_notification_log_status ON notification_log(status);

-- 初始化5座兵营数据
INSERT INTO barracks (name, code, location, description, capacity) VALUES
('第一兵营（南营）', 'BARRACKS_001', ST_SetSRID(ST_MakePoint(100.2700, 41.8500), 4326), '汉代居延遗址复原南营区', 120),
('第二兵营（北营）', 'BARRACKS_002', ST_SetSRID(ST_MakePoint(100.2710, 41.8520), 4326), '汉代居延遗址复原北营区', 100),
('第三兵营（东营）', 'BARRACKS_003', ST_SetSRID(ST_MakePoint(100.2730, 41.8510), 4326), '汉代居延遗址复原东营区', 110),
('第四兵营（西营）', 'BARRACKS_004', ST_SetSRID(ST_MakePoint(100.2680, 41.8510), 4326), '汉代居延遗址复原西营区', 90),
('第五兵营（中营）', 'BARRACKS_005', ST_SetSRID(ST_MakePoint(100.2710, 41.8510), 4326), '汉代居延遗址复原中营指挥区', 80)
ON CONFLICT (code) DO NOTHING;

-- 初始化士兵数据（每个兵营若干士兵）
-- 第一兵营
INSERT INTO soldier (name, soldier_code, barracks_id, age, rank, origin_region, position, position_x, position_y, status)
SELECT 
    '士兵张' || g,
    'S001_' || LPAD(g::TEXT, 3, '0'),
    1,
    20 + (g % 25),
    CASE WHEN g % 10 = 0 THEN '什长' WHEN g % 50 = 0 THEN '屯长' ELSE '士卒' END,
    CASE WHEN g % 3 = 0 THEN 'NORTH' WHEN g % 3 = 1 THEN 'SOUTH' ELSE 'WEST' END,
    ST_SetSRID(ST_MakePoint(100.2700 + (g % 10) * 0.00005, 41.8500 + (g / 10) * 0.00005), 4326),
    50 + (g % 10) * 15,
    50 + (g / 10) * 15,
    'HEALTHY'
FROM generate_series(1, 50) g
ON CONFLICT (soldier_code) DO NOTHING;

-- 第二兵营
INSERT INTO soldier (name, soldier_code, barracks_id, age, rank, origin_region, position, position_x, position_y, status)
SELECT 
    '士兵李' || g,
    'S002_' || LPAD(g::TEXT, 3, '0'),
    2,
    20 + (g % 25),
    CASE WHEN g % 10 = 0 THEN '什长' WHEN g % 50 = 0 THEN '屯长' ELSE '士卒' END,
    CASE WHEN g % 3 = 0 THEN 'NORTH' WHEN g % 3 = 1 THEN 'SOUTH' ELSE 'WEST' END,
    ST_SetSRID(ST_MakePoint(100.2710 + (g % 10) * 0.00005, 41.8520 + (g / 10) * 0.00005), 4326),
    50 + (g % 10) * 15,
    50 + (g / 10) * 15,
    'HEALTHY'
FROM generate_series(1, 40) g
ON CONFLICT (soldier_code) DO NOTHING;

-- 第三兵营
INSERT INTO soldier (name, soldier_code, barracks_id, age, rank, origin_region, position, position_x, position_y, status)
SELECT 
    '士兵王' || g,
    'S003_' || LPAD(g::TEXT, 3, '0'),
    3,
    20 + (g % 25),
    CASE WHEN g % 10 = 0 THEN '什长' WHEN g % 50 = 0 THEN '屯长' ELSE '士卒' END,
    CASE WHEN g % 3 = 0 THEN 'NORTH' WHEN g % 3 = 1 THEN 'SOUTH' ELSE 'WEST' END,
    ST_SetSRID(ST_MakePoint(100.2730 + (g % 10) * 0.00005, 41.8510 + (g / 10) * 0.00005), 4326),
    50 + (g % 10) * 15,
    50 + (g / 10) * 15,
    'HEALTHY'
FROM generate_series(1, 45) g
ON CONFLICT (soldier_code) DO NOTHING;

-- 第四兵营
INSERT INTO soldier (name, soldier_code, barracks_id, age, rank, origin_region, position, position_x, position_y, status)
SELECT 
    '士兵赵' || g,
    'S004_' || LPAD(g::TEXT, 3, '0'),
    4,
    20 + (g % 25),
    CASE WHEN g % 10 = 0 THEN '什长' WHEN g % 50 = 0 THEN '屯长' ELSE '士卒' END,
    CASE WHEN g % 3 = 0 THEN 'NORTH' WHEN g % 3 = 1 THEN 'SOUTH' ELSE 'WEST' END,
    ST_SetSRID(ST_MakePoint(100.2680 + (g % 9) * 0.00005, 41.8510 + (g / 9) * 0.00005), 4326),
    50 + (g % 9) * 15,
    50 + (g / 9) * 15,
    'HEALTHY'
FROM generate_series(1, 35) g
ON CONFLICT (soldier_code) DO NOTHING;

-- 第五兵营
INSERT INTO soldier (name, soldier_code, barracks_id, age, rank, origin_region, position, position_x, position_y, status)
SELECT 
    '士兵刘' || g,
    'S005_' || LPAD(g::TEXT, 3, '0'),
    5,
    20 + (g % 25),
    CASE WHEN g % 10 = 0 THEN '什长' WHEN g % 50 = 0 THEN '屯长' ELSE '士卒' END,
    CASE WHEN g % 3 = 0 THEN 'NORTH' WHEN g % 3 = 1 THEN 'SOUTH' ELSE 'WEST' END,
    ST_SetSRID(ST_MakePoint(100.2710 + (g % 8) * 0.00005, 41.8510 + (g / 8) * 0.00005), 4326),
    50 + (g % 8) * 15,
    50 + (g / 8) * 15,
    'HEALTHY'
FROM generate_series(1, 30) g
ON CONFLICT (soldier_code) DO NOTHING;

-- 插入示例膳食记录（最近7天）
INSERT INTO meal_record (soldier_id, meal_type, meal_time, protein_g, fat_g, vitamin_c_mg, calorie_kcal, food_items)
SELECT 
    s.id,
    CASE h % 3
        WHEN 0 THEN 'BREAKFAST'
        WHEN 1 THEN 'LUNCH'
        ELSE 'DINNER'
    END,
    NOW() - (d || ' days')::INTERVAL + (h || ' hours')::INTERVAL,
    15 + (random() * 25),
    10 + (random() * 20),
    30 + (random() * 60),
    400 + (random() * 600),
    '粟米饭,烤肉,蔬菜汤'
FROM soldier s
CROSS JOIN generate_series(0, 6) d
CROSS JOIN generate_series(0, 2) h
ON CONFLICT DO NOTHING;

-- 插入示例体能消耗记录
INSERT INTO physical_activity (soldier_id, activity_date, activity_type, duration_minutes, calorie_burned)
SELECT 
    s.id,
    CURRENT_DATE - d,
    CASE 
        WHEN d % 4 = 0 THEN '军事训练'
        WHEN d % 4 = 1 THEN '巡逻执勤'
        WHEN d % 4 = 2 THEN '体力劳动'
        ELSE '日常操练'
    END,
    60 + (random() * 180),
    200 + (random() * 500)
FROM soldier s
CROSS JOIN generate_series(0, 6) d
ON CONFLICT DO NOTHING;

-- 创建视图：每日营养摄入汇总
CREATE OR REPLACE VIEW v_daily_nutrition AS
SELECT 
    s.id AS soldier_id,
    s.name AS soldier_name,
    s.barracks_id,
    DATE(m.meal_time) AS meal_date,
    SUM(m.protein_g) AS total_protein_g,
    SUM(m.fat_g) AS total_fat_g,
    SUM(m.vitamin_c_mg) AS total_vitamin_c_mg,
    SUM(m.calorie_kcal) AS total_calorie_kcal
FROM soldier s
JOIN meal_record m ON s.id = m.soldier_id
GROUP BY s.id, s.name, s.barracks_id, DATE(m.meal_time);

-- 创建视图：兵营肠道感染统计
CREATE OR REPLACE VIEW v_barracks_infection_stats AS
SELECT 
    b.id AS barracks_id,
    b.name AS barracks_name,
    DATE(f.sample_time) AS sample_date,
    COUNT(*) AS total_samples,
    SUM(CASE WHEN f.is_positive THEN 1 ELSE 0 END) AS positive_count,
    ROUND(
        SUM(CASE WHEN f.is_positive THEN 1 ELSE 0 END)::DECIMAL / 
        NULLIF(COUNT(*), 0) * 100, 
        2
    ) AS positive_rate_percent
FROM barracks b
JOIN fecal_sensor_data f ON b.id = f.barracks_id
GROUP BY b.id, b.name, DATE(f.sample_time);

-- ============================================================
-- 模块1: meal-planner 膳食方案优化
-- ============================================================

CREATE TABLE IF NOT EXISTS food_item (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    protein_per_100g DECIMAL(10,2) NOT NULL DEFAULT 0,
    fat_per_100g DECIMAL(10,2) NOT NULL DEFAULT 0,
    vitamin_c_per_100g_mg DECIMAL(10,2) NOT NULL DEFAULT 0,
    calorie_per_100g_kcal DECIMAL(10,2) NOT NULL DEFAULT 0,
    cost_per_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS meal_plan (
    id BIGSERIAL PRIMARY KEY,
    barracks_id BIGINT REFERENCES barracks(id),
    plan_name VARCHAR(200),
    start_date DATE,
    end_date DATE,
    target_soldier_count INTEGER,
    total_cost DECIMAL(12,2),
    daily_protein_g DECIMAL(10,2),
    daily_fat_g DECIMAL(10,2),
    daily_vitamin_c_mg DECIMAL(10,2),
    daily_calorie_kcal DECIMAL(10,2),
    solver_status VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS meal_plan_item (
    id BIGSERIAL PRIMARY KEY,
    meal_plan_id BIGINT REFERENCES meal_plan(id) ON DELETE CASCADE,
    day_of_week INTEGER NOT NULL,
    meal_type VARCHAR(20) NOT NULL,
    food_item_id BIGINT REFERENCES food_item(id),
    food_name VARCHAR(100),
    quantity_grams DECIMAL(10,2),
    cost DECIMAL(12,2),
    protein_g DECIMAL(10,2),
    fat_g DECIMAL(10,2),
    vitamin_c_mg DECIMAL(10,2),
    calorie_kcal DECIMAL(10,2)
);

CREATE INDEX idx_meal_plan_barracks ON meal_plan(barracks_id);
CREATE INDEX idx_meal_plan_item_plan ON meal_plan_item(meal_plan_id);

-- ============================================================
-- 模块2: seir-simulator 疫情传播模型
-- ============================================================

CREATE TABLE IF NOT EXISTS contact_edge (
    id BIGSERIAL PRIMARY KEY,
    barracks_id BIGINT REFERENCES barracks(id),
    soldier_id_a BIGINT REFERENCES soldier(id),
    soldier_id_b BIGINT REFERENCES soldier(id),
    contact_type VARCHAR(30),
    contact_frequency_per_day DOUBLE PRECISION,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS seir_simulation (
    id BIGSERIAL PRIMARY KEY,
    barracks_id BIGINT REFERENCES barracks(id),
    simulation_name VARCHAR(200),
    virus_type VARCHAR(50) NOT NULL DEFAULT '诺如病毒',
    simulation_days INTEGER,
    initial_infected_count INTEGER,
    transmission_rate_beta DECIMAL(8,4),
    latent_rate_sigma DECIMAL(8,4),
    recovery_rate_gamma DECIMAL(8,4),
    isolation_effectiveness DECIMAL(5,2),
    quarantine_start_day INTEGER,
    max_infected_count INTEGER,
    total_infected_count INTEGER,
    peak_day INTEGER,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS seir_time_point (
    id BIGSERIAL PRIMARY KEY,
    simulation_id BIGINT REFERENCES seir_simulation(id) ON DELETE CASCADE,
    day INTEGER NOT NULL,
    susceptible_count INTEGER,
    exposed_count INTEGER,
    infected_count INTEGER,
    recovered_count INTEGER,
    quarantined_count INTEGER
);

CREATE INDEX idx_contact_edge_barracks ON contact_edge(barracks_id);
CREATE INDEX idx_seir_simulation_barracks ON seir_simulation(barracks_id);
CREATE INDEX idx_seir_timepoint_sim ON seir_time_point(simulation_id);

-- ============================================================
-- 模块3: intervention-tree 营养干预推荐
-- ============================================================

CREATE TABLE IF NOT EXISTS intervention_rule (
    id BIGSERIAL PRIMARY KEY,
    rule_code VARCHAR(50),
    tree_node_path VARCHAR(200),
    condition_risk_level VARCHAR(20),
    condition_age_min INTEGER,
    condition_age_max INTEGER,
    condition_origin_region VARCHAR(20),
    condition_protein_risk_min DECIMAL(5,2),
    condition_vitamin_c_risk_min DECIMAL(5,2),
    condition_fat_risk_min DECIMAL(5,2),
    recommendation_supplement VARCHAR(200),
    recommendation_dosage VARCHAR(200),
    recommendation_duration_days INTEGER,
    supplement_cost_per_day DECIMAL(10,2),
    support_count INTEGER,
    confidence DECIMAL(5,2),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS intervention_recommendation (
    id BIGSERIAL PRIMARY KEY,
    soldier_id BIGINT REFERENCES soldier(id),
    soldier_name VARCHAR(100),
    risk_level VARCHAR(20),
    overall_risk_score DECIMAL(5,4),
    matched_rule_ids VARCHAR(500),
    recommended_supplements VARCHAR(1000),
    recommended_dosage VARCHAR(1000),
    estimated_cost_total DECIMAL(12,2),
    duration_days INTEGER,
    status VARCHAR(30) DEFAULT 'PENDING',
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    implemented_at TIMESTAMP
);

CREATE INDEX idx_intervention_rec_soldier ON intervention_recommendation(soldier_id);
CREATE INDEX idx_intervention_rec_status ON intervention_recommendation(status);

-- ============================================================
-- 模块4: supply-apriori 后勤补给漏洞分析
-- ============================================================

CREATE TABLE IF NOT EXISTS supply_deficit_record (
    id BIGSERIAL PRIMARY KEY,
    barracks_id BIGINT REFERENCES barracks(id),
    deficit_date DATE NOT NULL,
    food_category VARCHAR(50),
    food_name VARCHAR(100),
    standard_ration_g DECIMAL(12,2),
    actual_delivered_g DECIMAL(12,2),
    deficit_amount_g DECIMAL(12,2),
    deficit_rate DECIMAL(5,2),
    weather_condition VARCHAR(30),
    supply_route VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS association_rule (
    id BIGSERIAL PRIMARY KEY,
    antecedent_items VARCHAR(500),
    consequent_items VARCHAR(500),
    support DECIMAL(6,4),
    confidence DECIMAL(6,4),
    lift DECIMAL(8,4),
    antecedent_count INTEGER,
    consequent_count INTEGER,
    both_count INTEGER,
    total_transactions INTEGER,
    rule_description VARCHAR(500),
    is_significant BOOLEAN,
    analyzed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_supply_deficit_date ON supply_deficit_record(deficit_date);
CREATE INDEX idx_supply_deficit_barracks ON supply_deficit_record(barracks_id);
CREATE INDEX idx_assoc_rule_significant ON association_rule(is_significant);

-- ============================================================
-- 种子数据：汉代典型食材目录 (food_item)
-- ============================================================

INSERT INTO food_item (name, category, protein_per_100g, fat_per_100g, vitamin_c_per_100g_mg, calorie_per_100g_kcal, cost_per_kg, is_available) VALUES
('粟米', '主食', 9.7, 3.5, 0, 360, 8.0, TRUE),
('小麦', '主食', 11.9, 1.3, 0, 339, 7.5, TRUE),
('稻米', '主食', 7.4, 0.8, 0, 346, 9.0, TRUE),
('大豆', '豆制品', 35.0, 16.0, 0, 359, 6.0, TRUE),
('豆腐', '豆制品', 8.1, 3.7, 0, 70, 5.0, TRUE),
('牛肉', '肉类', 20.2, 2.3, 0, 125, 80.0, TRUE),
('羊肉', '肉类', 19.0, 14.1, 0, 203, 70.0, TRUE),
('猪肉', '肉类', 13.2, 37.0, 0, 395, 55.0, TRUE),
('鸡肉', '肉类', 19.3, 9.4, 0, 167, 45.0, TRUE),
('鸡蛋', '蛋类', 13.3, 8.8, 0, 144, 12.0, TRUE),
('葵菜', '蔬菜', 1.5, 0.2, 28, 23, 6.0, TRUE),
('韭菜', '蔬菜', 2.4, 0.4, 24, 26, 8.0, TRUE),
('萝卜', '蔬菜', 0.9, 0.1, 21, 16, 4.0, TRUE),
('白菜', '蔬菜', 1.5, 0.1, 28, 17, 5.0, TRUE),
('干枣', '干果', 3.2, 0.5, 14, 247, 30.0, TRUE),
('栗子', '干果', 4.2, 0.7, 24, 185, 25.0, TRUE),
('蜂蜜', '调味品', 0.4, 1.9, 3, 303, 60.0, TRUE),
('芝麻油', '油脂', 0, 99.7, 0, 898, 40.0, TRUE),
('盐', '调味品', 0, 0, 0, 0, 3.0, TRUE),
('醋', '调味品', 2.1, 0.3, 0, 31, 8.0, TRUE),
('咸鱼', '腌制', 29.0, 10.0, 0, 205, 35.0, TRUE),
('酱菜', '腌制', 2.5, 0.4, 15, 30, 10.0, TRUE);

-- ============================================================
-- 种子数据：士兵接触网络 (contact_edge)
-- 模拟：同宿 (ROOMMATE, 10次/天)、同桌 (TABLE, 4次/天)
-- ============================================================

DO $$
DECLARE
    s RECORD;
    i INTEGER;
    j INTEGER;
    bunk_start INTEGER;
    soldiers_in_barracks INTEGER[];
    idx INTEGER;
    barracks RECORD;
BEGIN
    FOR barracks IN SELECT id FROM barracks LOOP
        SELECT ARRAY_AGG(id) INTO soldiers_in_barracks
        FROM soldier WHERE barracks_id = barracks.id;

        IF array_length(soldiers_in_barracks, 1) IS NULL THEN
            CONTINUE;
        END IF;

        FOR idx IN 1 .. array_length(soldiers_in_barracks, 1) BY 4 LOOP
            IF idx + 3 <= array_length(soldiers_in_barracks, 1) THEN
                FOR i IN 0..3 LOOP
                    FOR j IN (i+1)..3 LOOP
                        INSERT INTO contact_edge (barracks_id, soldier_id_a, soldier_id_b, contact_type, contact_frequency_per_day)
                        VALUES (barracks.id, soldiers_in_barracks[idx+i], soldiers_in_barracks[idx+j], 'ROOMMATE', 10.0);
                    END LOOP;
                END LOOP;
            END IF;
        END LOOP;

        FOR idx IN 1 .. array_length(soldiers_in_barracks, 1) BY 8 LOOP
            IF idx + 7 <= array_length(soldiers_in_barracks, 1) THEN
                FOR i IN 0..7 LOOP
                    FOR j IN (i+1)..7 LOOP
                        IF random() < 0.4 THEN
                            INSERT INTO contact_edge (barracks_id, soldier_id_a, soldier_id_b, contact_type, contact_frequency_per_day)
                            VALUES (barracks.id, soldiers_in_barracks[idx+i], soldiers_in_barracks[idx+j], 'TABLE', 4.0);
                        END IF;
                    END LOOP;
                END LOOP;
            END IF;
        END LOOP;
    END LOOP;
END $$;

-- ============================================================
-- 种子数据：内置营养干预规则 (intervention_rule)
-- ============================================================

INSERT INTO intervention_rule (rule_code, tree_node_path, condition_risk_level, condition_age_min, condition_age_max, condition_origin_region, condition_protein_risk_min, condition_vitamin_c_risk_min, condition_fat_risk_min, recommendation_supplement, recommendation_dosage, recommendation_duration_days, supplement_cost_per_day, support_count, confidence, is_active) VALUES
('RULE-001', 'Root->VitaminC>0.7', NULL, NULL, NULL, NULL, NULL, 0.70, NULL, '干枣+新鲜蔬菜', '干枣每日30g，新鲜蔬菜每日200g', 14, 3.50, 128, 0.92, TRUE),
('RULE-002', 'Root->Protein>0.7', NULL, NULL, NULL, NULL, 0.70, NULL, NULL, '肉干+豆制品', '肉干每日50g，豆制品每日100g', 14, 6.50, 105, 0.88, TRUE),
('RULE-003', 'Root->Fat>0.7', NULL, NULL, NULL, NULL, NULL, NULL, 0.70, '坚果+芝麻油', '坚果每日30g，芝麻油每日10ml', 14, 4.20, 72, 0.81, TRUE),
('RULE-004', 'Root->Age>40', NULL, 41, 99, NULL, NULL, NULL, NULL, '鱼肝油', '鱼肝油每日5ml', 30, 2.00, 56, 0.75, TRUE),
('RULE-005', 'Root->Region=SOUTH', NULL, NULL, NULL, 'SOUTH', NULL, NULL, NULL, '豆制品(比例+30%)', '豆腐每日130g', 14, 1.95, 88, 0.78, TRUE),
('RULE-006', 'Root->Region=NORTH', NULL, NULL, NULL, 'NORTH', NULL, NULL, NULL, '新鲜蔬菜(比例+30%)', '葵菜+白菜每日260g', 14, 2.50, 92, 0.80, TRUE),
('RULE-007', 'Root->RiskLevel=CRITICAL', 'CRITICAL', NULL, NULL, NULL, NULL, NULL, NULL, '综合补给包(干枣+肉干+蔬菜+鱼肝油)', '每日综合补给1份', 21, 12.00, 45, 0.95, TRUE),
('RULE-008', 'Root->RiskLevel=HIGH->Age<30', 'HIGH', 16, 30, NULL, NULL, NULL, NULL, '高能量补给(坚果+鸡蛋)', '坚果每日40g，鸡蛋每日2个', 14, 5.80, 62, 0.85, TRUE),
('RULE-009', 'Root->VitaminC>0.5+Region=WEST', NULL, NULL, NULL, 'WEST', NULL, 0.50, NULL, '水果干+葵菜', '干枣40g，葵菜每日250g', 14, 4.80, 38, 0.82, TRUE),
('RULE-010', 'Root->Protein>0.5+Fat>0.5', NULL, NULL, NULL, NULL, 0.50, NULL, 0.50, '羊肉+豆腐', '羊肉每日80g，豆腐每日150g', 14, 9.50, 51, 0.79, TRUE);

-- ============================================================
-- 种子数据：模拟30天补给短缺记录 (supply_deficit_record)
-- 含关联模式：暴雨→蔬菜短缺、西线→豆制品/乳制品短缺
-- ============================================================

DO $$
DECLARE
    d DATE := CURRENT_DATE - 30;
    barracks RECORD;
    weather TEXT;
    route TEXT;
    categories TEXT[] := ARRAY['主食类','肉类','豆制品','蔬菜类','干果类','乳制品','蛋类'];
    cat TEXT;
    weather_list TEXT[] := ARRAY['晴','晴','晴','晴','多云','多云','多云','小雨','暴雨','大雪','沙尘暴'];
    route_map BIGINT[];
    rand DOUBLE PRECISION;
    standard_g DECIMAL(12,2);
    actual_g DECIMAL(12,2);
    deficit_rate DECIMAL(5,2);
    barracks_terrain TEXT;
BEGIN
    FOR i IN 1..30 LOOP
        d := d + 1;
        weather := weather_list[1 + FLOOR(random() * 11)::INTEGER];

        FOR barracks IN SELECT id, name FROM barracks LOOP
            IF barracks.name LIKE '%西%' OR barracks.name LIKE '%南%' THEN
                barracks_terrain := '山区';
                route_map := ARRAY[barracks.id, 2];
            ELSIF barracks.name LIKE '%北%' THEN
                barracks_terrain := '边境';
                route_map := ARRAY[barracks.id, 3];
            ELSE
                barracks_terrain := '平原';
                route_map := ARRAY[barracks.id, 1];
            END IF;

            IF random() < 0.33 THEN route := '东线';
            ELSIF random() < 0.66 THEN route := '西线';
            ELSE route := '北线';
            END IF;

            FOREACH cat IN ARRAY categories LOOP
                rand := random();

                standard_g := CASE cat
                    WHEN '主食类' THEN 800 + random()*200
                    WHEN '肉类' THEN 150 + random()*100
                    WHEN '豆制品' THEN 100 + random()*50
                    WHEN '蔬菜类' THEN 300 + random()*150
                    WHEN '干果类' THEN 30 + random()*20
                    WHEN '乳制品' THEN 50 + random()*30
                    WHEN '蛋类' THEN 80 + random()*40
                    ELSE 100
                END;

                deficit_rate := 0.02 + random() * 0.10;

                IF weather IN ('暴雨','大雪','沙尘暴') AND cat IN ('蔬菜类','豆制品','乳制品') THEN
                    deficit_rate := deficit_rate + 0.15 + random() * 0.30;
                END IF;

                IF route = '西线' AND cat IN ('豆制品','乳制品','肉类') AND barracks_terrain = '山区' THEN
                    deficit_rate := deficit_rate + 0.10 + random() * 0.20;
                END IF;

                IF route = '北线' AND barracks_terrain = '边境' AND cat IN ('蔬菜类','蛋类') THEN
                    deficit_rate := deficit_rate + 0.08 + random() * 0.15;
                END IF;

                IF deficit_rate > 0.95 THEN deficit_rate := 0.95; END IF;

                actual_g := standard_g * (1 - deficit_rate);

                IF deficit_rate >= 0.05 OR (weather IN ('暴雨','大雪','沙尘暴') AND random() < 0.7) THEN
                    INSERT INTO supply_deficit_record (barracks_id, deficit_date, food_category, food_name, standard_ration_g, actual_delivered_g, deficit_amount_g, deficit_rate, weather_condition, supply_route)
                    VALUES (
                        barracks.id,
                        d,
                        cat,
                        CASE cat
                            WHEN '主食类' THEN (CASE WHEN random()<0.5 THEN '粟米' ELSE '小麦' END)
                            WHEN '肉类' THEN (CASE WHEN random()<0.5 THEN '牛肉' ELSE '羊肉' END)
                            WHEN '豆制品' THEN (CASE WHEN random()<0.6 THEN '大豆' ELSE '豆腐' END)
                            WHEN '蔬菜类' THEN (CASE WHEN random()<0.5 THEN '葵菜' ELSE '白菜' END)
                            WHEN '干果类' THEN (CASE WHEN random()<0.7 THEN '干枣' ELSE '栗子' END)
                            WHEN '乳制品' THEN '酥油'
                            WHEN '蛋类' THEN '鸡蛋'
                            ELSE '其他'
                        END,
                        standard_g,
                        actual_g,
                        standard_g - actual_g,
                        deficit_rate,
                        weather,
                        route
                    );
                END IF;
            END LOOP;
        END LOOP;
    END LOOP;
END $$;
