package dev.archi.jobhub.ingest.greenhouse;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl        Job Board API root, without a trailing slash
 * @param connectTimeout TCP connect timeout per request
 * @param readTimeout    max wait for a response per request
 * @param fetchPay       if true, one extra detail call per job to read pay ranges (N+1)
 * @param payConcurrency max detail calls in flight at once when fetchPay is on
 */
@ConfigurationProperties("jobhub.greenhouse")
public record GreenhouseProperties(
		URI baseUrl,
		Duration connectTimeout,
		Duration readTimeout,
		boolean fetchPay,
		int payConcurrency) {
}
