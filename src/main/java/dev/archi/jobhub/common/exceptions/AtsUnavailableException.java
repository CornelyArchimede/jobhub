package dev.archi.jobhub.common.exceptions;

import dev.archi.jobhub.company.AtsType;

public class AtsUnavailableException extends JobHubException {

    private final AtsType atsType;
    private final String boardToken;

    public AtsUnavailableException(AtsType atsType, String boardToken) {
        // Pass a descriptive message to the parent exception class
        super(String.format("The %s ATS board with token '%s' is unavailable", atsType, boardToken));
        this.atsType = atsType;
        this.boardToken = boardToken;
    }

    public AtsUnavailableException(String message, AtsType atsType, String boardToken) {
        super(message);
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
