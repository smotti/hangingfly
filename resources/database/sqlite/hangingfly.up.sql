CREATE TABLE session(
session_id              VARCHAR(256) PRIMARY KEY NOT NULL,
salted_id               VARCHAR(256) NOT NULL,
start_time              BIGINT NOT NULL,
end_time                BIGINT,
cause_for_termination   VARCHAR(256),
previous_session_ids    TEXT,
is_valid                BOOLEAN NOT NULL,
absolute_timeout        INT NOT NULL,
idle_timeout            INT NOT NULL,
renewal_timeout         INT NOT NULL
);
--;;
CREATE TABLE session_attributes(
id          INTEGER PRIMARY KEY,
session_id  VARCHAR(256) NOT NULL,
name text   NOT NULL,
data_type   VARCHAR(16),
FOREIGN KEY(session_id) REFERENCES session(session_id) ON DELETE CASCADE
);
--;;
CREATE TABLE session_attribute_value(
id            INTEGER PRIMARY KEY,
attribute_id  INT NOT NULL,
string        TEXT,
number        INT,
date          DATETIME,
bool          BOOLEAN,
FOREIGN KEY(attribute_id) REFERENCES session_attribute(id) ON DELETE CASCADE
);
