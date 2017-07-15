CREATE OR REPLACE FUNCTION array_filter_regex_vals(text[])
RETURNS text[] AS $$
DECLARE
  s text;
  rest text[];
  filtered_rest text[];
  n int;
BEGIN
  n = array_length($1, 1);
  IF n >= 1 THEN
    s = $1[1];
    rest = $1[2:n];
    filtered_rest = array_filter_regex_vals(rest);
    IF s LIKE '^%$' THEN
      RETURN array_prepend(s, filtered_rest);
    ELSE
      RETURN filtered_rest;
    END IF;
  ELSE
    RETURN $1;
  END IF;
END;
$$ LANGUAGE 'plpgsql' ;
