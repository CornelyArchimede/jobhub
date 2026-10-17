package dev.archi.jobhub.ingest.greenhouse;

/**
 * Pay collapsed to a single band, in whole currency units (not cents).
 */
public record PayRange(int min, int max, String currency) {
}
