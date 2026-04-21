-- Function to generate NanoID in PostgreSQL
-- Usage: nanoid(length)
-- Example: nanoid(12)

CREATE OR REPLACE FUNCTION nanoid(size integer DEFAULT 21, alphabet text DEFAULT '_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ')
RETURNS text
LANGUAGE plpgsql
VOLATILE
AS $$
DECLARE
  id text := '';
  i integer := 0;
  bytes bytea;
  alphabet_size integer := length(alphabet);
  mask integer := (2 << cast(floor(log(alphabet_size - 1) / log(2)) as integer)) - 1;
  step integer := cast(ceil(1.6 * mask * size / alphabet_size) as integer);
BEGIN
  WHILE length(id) < size LOOP
    bytes := gen_random_bytes(step);
    FOR i IN 0..step - 1 LOOP
      DECLARE
        byte integer := get_byte(bytes, i) & mask;
      BEGIN
        IF byte < alphabet_size THEN
          id := id || substr(alphabet, byte + 1, 1);
          IF length(id) = size THEN
            RETURN id;
          END IF;
        END IF;
      END LOOP;
    END LOOP;
  END WHILE;
  RETURN id;
END
$$;
