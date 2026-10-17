package dev.archi.jobhub.ingest.exceptions;

import dev.archi.jobhub.common.exceptions.JobHubException;
import dev.archi.jobhub.company.AtsType;

public class BoardNotFoundException extends JobHubException {

	private final AtsType atsType;

	private final String boardToken;

	public BoardNotFoundException(AtsType atsType, String boardToken) {
		super("No " + atsType + " board with token '" + boardToken + "'");
		this.atsType = atsType;
		this.boardToken = boardToken;
	}

	public AtsType atsType() {
		return this.atsType;
	}

	public String boardToken() {
		return this.boardToken;
	}

}
