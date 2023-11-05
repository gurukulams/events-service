
CREATE TABLE events (
    id UUID PRIMARY KEY,
    title VARCHAR(55),
    description TEXT,
    event_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(55) NOT NULL,
    modified_at TIMESTAMP,
    modified_by VARCHAR(200)
);

CREATE TABLE events_localized (
    event_id UUID,
    locale VARCHAR(8) NOT NULL,
    title VARCHAR(55),
    description TEXT,
    FOREIGN KEY (event_id) REFERENCES events (id),
    PRIMARY KEY(event_id, locale)
);

CREATE TABLE events_category (
    event_id UUID NOT NULL,
    category_id VARCHAR(55) NOT NULL,
    PRIMARY KEY(event_id, category_id),
    FOREIGN KEY (event_id) REFERENCES events (id)
);

CREATE TABLE events_tag (
    event_id UUID NOT NULL,
    tag_id VARCHAR(55) NOT NULL,
    PRIMARY KEY(event_id, tag_id),
    FOREIGN KEY (event_id) REFERENCES events (id)
);
