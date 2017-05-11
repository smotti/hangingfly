CREATE USER hangingfly WITH PASSWORD 'hangingfly' CONNECTION LIMIT -1;
--;;
GRANT CONNECT ON DATABASE hfdb TO hangingfly;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE session TO hangingfly;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE session_attributes TO hangingfly;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE session_attribute_value TO hangingfly;
