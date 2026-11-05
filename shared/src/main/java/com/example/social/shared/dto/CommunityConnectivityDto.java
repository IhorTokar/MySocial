package com.example.social.shared.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Чи зв'язна спільнота: скільки учасників досяжні з одного вузла. */
public record CommunityConnectivityDto(long community,
                                       long communitySize,
                                       long reachableFromOneNode,
                                       @JsonProperty("isFullyConnected") boolean isFullyConnected) {}