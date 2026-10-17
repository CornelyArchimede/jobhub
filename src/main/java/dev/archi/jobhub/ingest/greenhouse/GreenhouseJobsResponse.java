package dev.archi.jobhub.ingest.greenhouse;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Wire format of the Greenhouse Job Board API. Only the fields we use are mapped;
 * everything else (data_compliance, ai_disclaimer, ...) is ignored.
 *
 * <p>List: {@code GET /boards/{token}/jobs?content=true}
 * <br>Detail: {@code GET /boards/{token}/jobs/{id}?pay_transparency=true}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GreenhouseJobsResponse(List<Job> jobs, @Nullable Meta meta) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Meta(int total) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Job(
			long id,
			String title,
			@Nullable Location location,
			@JsonProperty("absolute_url") String absoluteUrl,
			/** Entity-escaped HTML, e.g. {@code &lt;p&gt;}. Only present with content=true. */
			@Nullable String content,
			@JsonProperty("first_published") @Nullable Instant firstPublished,
			@JsonProperty("updated_at") @Nullable Instant updatedAt) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Location(@Nullable String name) {
	}

	/** Single-job response; the only extra field we need is pay. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JobDetail(
			long id,
			@JsonProperty("pay_input_ranges") @Nullable List<PayInputRange> payInputRanges) {
	}

	/** One pay band. Greenhouse may return several per job (e.g. one per location). */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PayInputRange(
			@JsonProperty("min_cents") long minCents,
			@JsonProperty("max_cents") long maxCents,
			@JsonProperty("currency_type") String currencyType,
			@Nullable String title) {
	}

}
