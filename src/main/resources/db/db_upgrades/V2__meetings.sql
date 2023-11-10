ALTER TABLE events DROP COLUMN meeting_url;

CREATE TABLE events_meeting (
    event_id UUID NOT NULL,
    meeting_url VARCHAR(200) NOT NULL,
    PRIMARY KEY(event_id),
    FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT events_meeting_url_constraint UNIQUE (meeting_url)
);
