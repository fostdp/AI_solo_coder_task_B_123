package com.juyan.barracks.epidemic.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SatscanEpidemicDetectorTest {

    private SatscanEpidemicDetector detector;
    private LocalDateTime baseTime;

    @BeforeEach
    void setUp() {
        detector = new SatscanEpidemicDetector(100.0, 7, 0.05);
        baseTime = LocalDateTime.of(2024, 1, 15, 12, 0);
    }

    @Test
    void testEmptyPoints() {
        List<SatscanEpidemicDetector.ClusterPoint> points = new ArrayList<>();
        SatscanEpidemicDetector.ScanResult result = detector.scanSpatiotemporal(points, baseTime);

        assertNotNull(result);
        assertFalse(result.isHasSignificantCluster());
        assertEquals(1.0, result.getPValue(), 0.001);
        assertEquals(0.0, result.getLogLikelihoodRatio(), 0.001);
        assertNotNull(result.getAffectedPoints());
        assertTrue(result.getAffectedPoints().isEmpty());
    }

    @Test
    void testNullPoints() {
        SatscanEpidemicDetector.ScanResult result = detector.scanSpatiotemporal(null, baseTime);

        assertNotNull(result);
        assertFalse(result.isHasSignificantCluster());
        assertEquals(1.0, result.getPValue(), 0.001);
    }

    @Test
    void testNoCases() {
        List<SatscanEpidemicDetector.ClusterPoint> points = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            points.add(createPoint(i * 10.0, 0.0, baseTime.minusHours(i * 2), false));
        }

        SatscanEpidemicDetector.ScanResult result = detector.scanSpatiotemporal(points, baseTime);

        assertFalse(result.isHasSignificantCluster());
        assertEquals(1.0, result.getPValue(), 0.001);
    }

    @Test
    void testAllCases() {
        List<SatscanEpidemicDetector.ClusterPoint> points = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            points.add(createPoint(i * 10.0, 0.0, baseTime.minusHours(i * 2), true));
        }

        SatscanEpidemicDetector.ScanResult result = detector.scanSpatiotemporal(points, baseTime);

        assertNotNull(result);
        assertEquals(1.0, result.getPValue(), 0.001);
    }

    @Test
    void testClusteredOutbreak() {
        List<SatscanEpidemicDetector.ClusterPoint> points = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            points.add(createPoint(
                    10.0 + Math.random() * 10.0,
                    10.0 + Math.random() * 10.0,
                    baseTime.minusHours((long) (Math.random() * 24)),
                    false
            ));
        }

        for (int i = 0; i < 15; i++) {
            points.add(createPoint(
                    60.0 + Math.random() * 5.0,
                    60.0 + Math.random() * 5.0,
                    baseTime.minusHours((long) (Math.random() * 12)),
                    true
            ));
        }

        SatscanEpidemicDetector.ScanResult result = detector.scanSpatiotemporal(points, baseTime);

        assertNotNull(result);
        assertNotNull(result.getClusterCenter());
        assertTrue(result.getCasesInCluster() > 0);
        assertTrue(result.getTotalInCluster() > 0);
        assertTrue(result.getPositiveRate() >= 0.0 && result.getPositiveRate() <= 1.0);
        assertTrue(result.getPValue() >= 0.0 && result.getPValue() <= 1.0);
    }

    @Test
    void testScanResultFields() {
        List<SatscanEpidemicDetector.ClusterPoint> points = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            points.add(createPoint(i * 5.0, i * 3.0, baseTime.minusHours(i), i < 3));
        }

        SatscanEpidemicDetector.ScanResult result = detector.scanSpatiotemporal(points, baseTime);

        assertNotNull(result);
        assertNotNull(result.getAffectedPoints());
        assertNotNull(result.getStartTime());
        assertNotNull(result.getEndTime());
        assertTrue(result.getClusterRadius() >= 0);
    }

    @Test
    void testClusterPointBuilder() {
        LocalDateTime time = LocalDateTime.now();
        SatscanEpidemicDetector.ClusterPoint point = SatscanEpidemicDetector.ClusterPoint.builder()
                .x(10.5)
                .y(20.5)
                .time(time)
                .isCase(true)
                .soldierId(100L)
                .build();

        assertEquals(10.5, point.getX());
        assertEquals(20.5, point.getY());
        assertEquals(time, point.getTime());
        assertTrue(point.isCase());
        assertEquals(100L, point.getSoldierId());
    }

    @Test
    void testDifferentDetectorConfig() {
        SatscanEpidemicDetector bigDetector = new SatscanEpidemicDetector(200.0, 14, 0.01);

        List<SatscanEpidemicDetector.ClusterPoint> points = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            points.add(createPoint(i * 8.0, 0.0, baseTime.minusHours(i * 3), i < 8));
        }

        SatscanEpidemicDetector.ScanResult result = bigDetector.scanSpatiotemporal(points, baseTime);
        assertNotNull(result);
        assertTrue(result.getPValue() >= 0.0 && result.getPValue() <= 1.0);
    }

    private SatscanEpidemicDetector.ClusterPoint createPoint(
            double x, double y, LocalDateTime time, boolean isCase) {
        return SatscanEpidemicDetector.ClusterPoint.builder()
                .x(x)
                .y(y)
                .time(time)
                .isCase(isCase)
                .soldierId((long) (x * 100 + y * 10 + time.getHour()))
                .build();
    }
}
