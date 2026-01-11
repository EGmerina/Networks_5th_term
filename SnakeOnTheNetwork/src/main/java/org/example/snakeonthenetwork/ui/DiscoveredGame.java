package org.example.snakeonthenetwork.ui;

import me.ippolitov.fit.snakes.SnakesProto;

public record DiscoveredGame(SnakesProto.GameAnnouncement announcement, Long lastUpdateTime) {
}
