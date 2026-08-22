package com.redhun.aiswarya_ledger_api.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import java.util.List;

@Getter
public class MembersPendingException extends BusinessException {

    private final List<String> pendingMemberNames;

    public MembersPendingException(long count, List<String> pendingMemberNames) {
        super(
            "MEMBERS_PENDING",
            String.format("Cannot complete the meeting. %d member(s) have not been processed: %s", count, String.join(", ", pendingMemberNames)),
            HttpStatus.BAD_REQUEST
        );
        this.pendingMemberNames = pendingMemberNames;
    }
}
