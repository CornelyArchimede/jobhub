package dev.archi.jobhub.ingest;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * ATS-agnostic job posting. Every AtsClient maps its own payload to this shape;
 * fields mirror the job_posting columns that arrive in M2.
 *
 * @param externalId      the ATS's own id for the posting, unique per company
 * @param payMin          whole currency units (not cents), null when the ATS doesn't publish pay
 * @param payMax          whole currency units (not cents), null when the ATS doesn't publish pay
 * @param payCurrency     ISO 4217 code, e.g. "USD"; null when pay is null
 * @param descriptionText plain text: HTML unescaped and tags stripped
 * @param postedAt        when the posting first went live
 * @param updatedAt       last change on the ATS side
 */
public record NormalizedJob(
		String externalId,
		String title,
		@Nullable String location,
		RemoteType remoteType,
		@Nullable String employmentType,
		@Nullable Integer payMin,
		@Nullable Integer payMax,
		@Nullable String payCurrency,
		String url,
		String descriptionText,
		@Nullable Instant postedAt,
		@Nullable Instant updatedAt) {
}
