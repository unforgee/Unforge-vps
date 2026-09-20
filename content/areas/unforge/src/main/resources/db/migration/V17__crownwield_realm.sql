-- CrownWield realm identity: home is Edgeville (3086, 3495, 0) per
-- io.ruin.model.entity.player.Player default position; login message mirrors
-- Player.java "Welcome to Crownwield PvP. The Final Challenge!"; Kronos grants
-- 1x base XP with situational multipliers, so rates drop from the dev 150x
-- default to 1x.
UPDATE realms
SET description = 'Crownwield PvP',
    login_message = 'Welcome to Crownwield PvP. The Final Challenge!',
    spawn_coord = '0_48_54_14_39',
    respawn_coord = '0_48_54_14_39',
    player_xp_rate_in_hundreds = 100,
    global_xp_rate_in_hundreds = 100
WHERE name = 'dev';
