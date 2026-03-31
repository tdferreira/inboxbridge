package dev.inboxbridge.dto;

import java.util.List;

public record RemoteControlView(
        RemoteSessionUserResponse session,
        List<RemoteSourceView> sources,
        boolean hasOwnSourceEmailAccounts,
        boolean hasReadyDestinationMailbox,
        boolean setupRequired,
        String remotePollRateLimitWindow,
        int remotePollRateLimitCount) {
}
