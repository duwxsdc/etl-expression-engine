package com.etl.engine.util;

import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PerformanceTracker {

    private static final ThreadLocal<PerformanceContext> CONTEXT = new ThreadLocal<>();

    private PerformanceTracker() {}

    public static void startTracking(String requestId, String operation) {
        PerformanceContext ctx = new PerformanceContext(requestId, operation);
        CONTEXT.set(ctx);
    }

    public static void recordPhase(String phaseName) {
        PerformanceContext ctx = CONTEXT.get();
        if (ctx != null) {
            ctx.recordPhase(phaseName);
        }
    }

    public static void recordPhase(String phaseName, String detail) {
        PerformanceContext ctx = CONTEXT.get();
        if (ctx != null) {
            ctx.recordPhase(phaseName, detail);
        }
    }

    public static long getPhaseDuration(String phaseName) {
        PerformanceContext ctx = CONTEXT.get();
        if (ctx != null) {
            return ctx.getPhaseDuration(phaseName);
        }
        return 0;
    }

    public static PerformanceSummary endTracking() {
        PerformanceContext ctx = CONTEXT.get();
        if (ctx != null) {
            CONTEXT.remove();
            return ctx.getSummary();
        }
        return null;
    }

    public static void logPerformance(Logger logger) {
        PerformanceSummary summary = endTracking();
        if (summary != null) {
            RequestLogger.logStage(logger, summary.getRequestId(), "PERFORMANCE_SUMMARY",
                    "Performance metrics collected", summary.toMap());
        }
    }

    public static class PerformanceContext {
        private final String requestId;
        private final String operation;
        private final long startTime;
        private final Map<String, PhaseRecord> phases = new LinkedHashMap<>();
        private String currentPhase;
        private long currentPhaseStart;

        public PerformanceContext(String requestId, String operation) {
            this.requestId = requestId;
            this.operation = operation;
            this.startTime = System.currentTimeMillis();
        }

        public void recordPhase(String phaseName) {
            long now = System.currentTimeMillis();
            
            if (currentPhase != null) {
                long duration = now - currentPhaseStart;
                phases.computeIfAbsent(currentPhase, k -> new PhaseRecord())
                      .addDuration(duration);
            }
            
            currentPhase = phaseName;
            currentPhaseStart = now;
        }

        public void recordPhase(String phaseName, String detail) {
            recordPhase(phaseName);
            PhaseRecord record = phases.get(phaseName);
            if (record != null) {
                record.setDetail(detail);
            }
        }

        public long getPhaseDuration(String phaseName) {
            PhaseRecord record = phases.get(phaseName);
            return record != null ? record.getTotalDuration() : 0;
        }

        public PerformanceSummary getSummary() {
            if (currentPhase != null) {
                long duration = System.currentTimeMillis() - currentPhaseStart;
                phases.computeIfAbsent(currentPhase, k -> new PhaseRecord())
                      .addDuration(duration);
            }
            
            long totalDuration = System.currentTimeMillis() - startTime;
            return new PerformanceSummary(requestId, operation, startTime, totalDuration, phases);
        }
    }

    public static class PhaseRecord {
        private long totalDuration;
        private int callCount;
        private String detail;

        public void addDuration(long duration) {
            this.totalDuration += duration;
            this.callCount++;
        }

        public long getTotalDuration() {
            return totalDuration;
        }

        public int getCallCount() {
            return callCount;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }
    }

    public static class PerformanceSummary {
        private final String requestId;
        private final String operation;
        private final long startTime;
        private final long totalDuration;
        private final Map<String, PhaseRecord> phases;

        public PerformanceSummary(String requestId, String operation, long startTime, 
                                  long totalDuration, Map<String, PhaseRecord> phases) {
            this.requestId = requestId;
            this.operation = operation;
            this.startTime = startTime;
            this.totalDuration = totalDuration;
            this.phases = phases;
        }

        public String getRequestId() {
            return requestId;
        }

        public String getOperation() {
            return operation;
        }

        public long getTotalDuration() {
            return totalDuration;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("operation", operation);
            map.put("totalDurationMs", totalDuration);
            
            Map<String, Object> phaseDetails = new LinkedHashMap<>();
            for (Map.Entry<String, PhaseRecord> entry : phases.entrySet()) {
                Map<String, Object> phaseInfo = new LinkedHashMap<>();
                phaseInfo.put("durationMs", entry.getValue().getTotalDuration());
                phaseInfo.put("callCount", entry.getValue().getCallCount());
                if (entry.getValue().getDetail() != null) {
                    phaseInfo.put("detail", entry.getValue().getDetail());
                }
                double percentage = totalDuration > 0 ? 
                    (double) entry.getValue().getTotalDuration() / totalDuration * 100 : 0;
                phaseInfo.put("percentage", String.format("%.1f%%", percentage));
                phaseDetails.put(entry.getKey(), phaseInfo);
            }
            map.put("phases", phaseDetails);
            
            return map;
        }

        public void logBottleneckAnalysis(Logger logger) {
            String bottleneck = null;
            long maxDuration = 0;
            
            for (Map.Entry<String, PhaseRecord> entry : phases.entrySet()) {
                if (entry.getValue().getTotalDuration() > maxDuration) {
                    maxDuration = entry.getValue().getTotalDuration();
                    bottleneck = entry.getKey();
                }
            }
            
            if (bottleneck != null && maxDuration > totalDuration * 0.3) {
                Map<String, Object> analysis = new LinkedHashMap<>();
                analysis.put("bottleneckPhase", bottleneck);
                analysis.put("bottleneckDurationMs", maxDuration);
                analysis.put("bottleneckPercentage", String.format("%.1f%%", 
                    (double) maxDuration / totalDuration * 100));
                analysis.put("recommendation", "Consider optimizing " + bottleneck + " for better performance");
                
                RequestLogger.logWarn(logger, requestId, "PERFORMANCE_BOTTLENECK",
                        "Potential performance bottleneck detected", analysis);
            }
        }
    }
}
