package de.aetherion.core.api;

import java.util.List;

/**
 * Point-in-time QA test-bot snapshot for Dev menu and {@code /botreport}.
 */
public record TestBotReport(
        boolean enabled,
        boolean runnerReachable,
        String runnerDetail,
        double tps,
        int onlineTotal,
        int maxTotal,
        List<TestBotRoleView> roles,
        List<TestBotView> bots,
        long generatedAtMs
) {
    public TestBotReport {
        roles = roles == null ? List.of() : List.copyOf(roles);
        bots = bots == null ? List.of() : List.copyOf(bots);
        runnerDetail = runnerDetail == null ? "" : runnerDetail;
    }
}
