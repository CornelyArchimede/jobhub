package dev.archi.jobhub.common.exceptions;

import dev.archi.jobhub.company.AtsType;

/**
 * The ATS failed (5xx, timeout, unreadable body). The message is ours; the upstream
 * error is kept as the cause so it's logged but never returned to API callers.
 */
public class AtsUnavailableException extends JobHubException {

	private final AtsType atsType;

	private final String boardToken;

	public AtsUnavailableException(AtsType atsType, String boardToken) {
		super(message(atsType, boardToken));
		this.atsType = atsType;
		this.boardToken = boardToken;
	}

	public AtsUnavailableException(AtsType atsType, String boardToken, Throwable cause) {
		super(message(atsType, boardToken), cause);
		this.atsType = atsType;
		this.boardToken = boardToken;
	}

	private static String message(AtsType atsType, String boardToken) {
		return String.format("The %s board '%s' is unavailable", atsType, boardToken);
	}

	public AtsType atsType() {
		return this.atsType;
	}

	public String boardToken() {
		return this.boardToken;
	}

}
