package dev.archi.jobhub.ingest;

import java.util.List;

import dev.archi.jobhub.company.AtsType;

/**
 * Fetches every open posting on one company's job board and normalizes it.
 * One implementation per ATS (Greenhouse in M1, Lever in M3, Ashby later).
 */
public interface AtsClient {

	AtsType type();

	/**
	 * @throws BoardNotFoundException if the ATS has no board with this token
	 */
	List<NormalizedJob> fetchJobs(String boardToken);

}
