package com.devteria.identityservice.dto;

import com.devteria.identityservice.dto.response.PlayerActionDto;

public record FastPathResult(
        FastPathType type,
        String responseText,
        PlayerActionDto playerAction
) {
    public enum FastPathType {
        TEXT_ONLY,
        PLAYER_ACTION
    }

    public static FastPathResult textMessage(String text) {
        return new FastPathResult(FastPathType.TEXT_ONLY, text, null);
    }

    public static FastPathResult playerAction(String action, String text) {
        PlayerActionDto actionDto = PlayerActionDto.builder()
                .action(action)
                .autoExecute(true)
                .song(null)
                .build();
        return new FastPathResult(FastPathType.PLAYER_ACTION, text, actionDto);
    }
}
