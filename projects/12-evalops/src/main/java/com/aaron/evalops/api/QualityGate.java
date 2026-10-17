package com.aaron.evalops.api;

/** Shared threshold calculation for versioned portfolio evaluations. */
final class QualityGate {
    private QualityGate() { }

    static void validateMinimum(double minimumPassRate) {
        if (!Double.isFinite(minimumPassRate) || minimumPassRate < 0 || minimumPassRate > 1) {
            throw new IllegalArgumentException("minimum pass rate must be between 0 and 1");
        }
    }

    static Result evaluate(long passed, long total, double minimumPassRate) {
        validateMinimum(minimumPassRate);
        if (passed < 0 || total < 0 || passed > total) throw new IllegalArgumentException("passed and total counts are invalid");
        double passRate = total == 0 ? 0 : (double) passed / total;
        return new Result(passRate, minimumPassRate, total > 0 && passRate >= minimumPassRate);
    }

    record Result(double passRate, double minimumPassRate, boolean qualityGatePassed) { }
}
