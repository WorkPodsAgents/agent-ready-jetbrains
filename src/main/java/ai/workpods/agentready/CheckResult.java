package ai.workpods.agentready;

import org.jetbrains.annotations.Nullable;

/**
 * One check's answer: a one-sentence summary, a shareable report page kept 30 days (null for
 * checks that keep none) and the full result as JSON.
 */
record CheckResult(String summary, @Nullable String reportUrl, String resultJson) {}
