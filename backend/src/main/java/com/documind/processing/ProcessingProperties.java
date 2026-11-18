package com.documind.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "documind.processing")
public class ProcessingProperties {

    /** local = in-process worker, rabbit = RabbitMQ queue. */
    private String mode = "local";

    private int maxAttempts = 3;

    private boolean sweepEnabled = true;

    private long sweepIntervalMs = 5000;

    /** Re-publish threshold for jobs stuck in QUEUED. */
    private long staleAfterSeconds = 120;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public boolean isSweepEnabled() {
        return sweepEnabled;
    }

    public void setSweepEnabled(boolean sweepEnabled) {
        this.sweepEnabled = sweepEnabled;
    }

    public long getSweepIntervalMs() {
        return sweepIntervalMs;
    }

    public void setSweepIntervalMs(long sweepIntervalMs) {
        this.sweepIntervalMs = sweepIntervalMs;
    }

    public long getStaleAfterSeconds() {
        return staleAfterSeconds;
    }

    public void setStaleAfterSeconds(long staleAfterSeconds) {
        this.staleAfterSeconds = staleAfterSeconds;
    }
}
