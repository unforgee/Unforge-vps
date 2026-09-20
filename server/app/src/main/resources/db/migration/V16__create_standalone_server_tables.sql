CREATE TABLE donor_points (
    player_id INTEGER PRIMARY KEY,
    points INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE donor_rank (
    player_id INTEGER PRIMARY KEY,
    rank TEXT NOT NULL
);

CREATE TABLE prestige (
    player_id INTEGER PRIMARY KEY,
    prestige_level INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE construction_houses (
    house_id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_id INTEGER NOT NULL,
    location TEXT NOT NULL,
    built_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE quest_progress (
    player_id INTEGER NOT NULL,
    quest_id INTEGER NOT NULL,
    status TEXT NOT NULL,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    PRIMARY KEY (player_id, quest_id)
);
