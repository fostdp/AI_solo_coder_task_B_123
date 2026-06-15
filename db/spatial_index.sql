-- 空间索引优化脚本 - 解决士兵密集时空间查询慢的问题
-- 根因: init.sql中虽已建基础GiST索引，但缺少复合索引和聚类分析专用索引
-- 修复: 补充GiST复合空间索引、VACUUM ANALYZE统计信息更新

-- 1. 士兵位置GiST索引（若不存在则创建，确保空间查询走索引扫描）
CREATE INDEX IF NOT EXISTS idx_soldier_position_gist
ON soldier USING GIST (position);

-- 2. 士兵按兵营+位置的复合GiST索引，加速"某兵营内空间范围查询"
CREATE INDEX IF NOT EXISTS idx_soldier_barracks_position
ON soldier USING GIST (barracks_id, position);

-- 3. 疫情告警聚类中心的GiST索引，加速空间聚类检索
CREATE INDEX IF NOT EXISTS idx_epidemic_alert_cluster_gist
ON epidemic_alert USING GIST (cluster_center)
WHERE cluster_center IS NOT NULL;

-- 4. 营养传感器数据按兵营+采样时间复合索引，加速时空联合查询
CREATE INDEX IF NOT EXISTS idx_nutrition_sensor_barracks_time
ON nutrition_sensor_data (barracks_id, sample_time);

-- 5. 粪便传感器数据按兵营+采样时间复合索引，加速感染率统计
CREATE INDEX IF NOT EXISTS idx_fecal_sensor_barracks_time
ON fecal_sensor_data (barracks_id, sample_time);

-- 6. 粪便传感器数据按兵营+阳性+时间复合索引，加速阳性率计算
CREATE INDEX IF NOT EXISTS idx_fecal_sensor_barracks_positive_time
ON fecal_sensor_data (barracks_id, is_positive, sample_time);

-- 7. 士兵按兵营+状态复合索引，加速"某兵营内健康/感染士兵"筛选
CREATE INDEX IF NOT EXISTS idx_soldier_barracks_status
ON soldier (barracks_id, status);

-- 8. 营养风险按风险等级+当前标记复合索引，加速高风险士兵检索
CREATE INDEX IF NOT EXISTS idx_nutrition_risk_level_current
ON nutrition_risk (risk_level, is_current)
WHERE is_current = true;

-- 9. 膳食记录按士兵+时间复合索引（覆盖索引优化），加速7天均值计算
CREATE INDEX IF NOT EXISTS idx_meal_record_soldier_time_cover
ON meal_record (soldier_id, meal_time)
INCLUDE (protein_g, fat_g, vitamin_c_mg, calorie_kcal);

-- 10. 更新统计信息，确保查询规划器选择最优执行计划
ANALYZE barracks;
ANALYZE soldier;
ANALYZE meal_record;
ANALYZE physical_activity;
ANALYZE nutrition_sensor_data;
ANALYZE fecal_sensor_data;
ANALYZE nutrition_risk;
ANALYZE epidemic_alert;
ANALYZE notification_log;

-- 11. 对soldier表按空间位置聚类，提升GiST索引空间局部性
-- 注意：CLUSTER会锁表，请在低峰期执行
-- CLUSTER soldier USING idx_soldier_position_gist;
