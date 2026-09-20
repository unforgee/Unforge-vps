-- Rename the visible login message for the Unforge eco server.
-- This is a new migration so existing databases receive the change as well.
UPDATE realms
SET description = 'Unforge unique eco server',
    login_message = 'Unforge unique eco server'
WHERE name = 'dev';
