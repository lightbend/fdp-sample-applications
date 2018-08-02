
CREATE KEYSPACE IF NOT EXISTS isd_weather_data WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };

use isd_weather_data;


CREATE TABLE IF NOT EXISTS weather_station (
    id text PRIMARY KEY,  // Composite of Air Force Datsav3 station number and NCDC WBAN number
    name text,            // Name of reporting station
    country_code text,    // 2 letter ISO Country ID
    state_code text,      // 2 letter state code for US stations
    call_sign text,       // International station call sign
    lat double,            // Latitude in decimal degrees
    long double,           // Longitude in decimal degrees
    elevation double       // Elevation in meters
);

/*
Raw weather readings from a single station, hourly.
sky_condition_text text, // Non-coded sky conditions
*/

CREATE TABLE IF NOT EXISTS raw_weather_data (
   wsid text,               // Composite of Air Force Datsav3 station number and NCDC WBAN number
   year int,                // Year collected
   month int,               // Month collected
   day int,                 // Day collected
   hour int,                // Hour collected
   temperature double,       // Air temperature (degrees Celsius)
   dewpoint double,          // Dew point temperature (degrees Celsius)
   pressure double,          // Sea level pressure (hectopascals)
   wind_direction int,      // Wind direction in degrees. 0-359
   wind_speed double,        // Wind speed (meters per second)
   sky_condition int,       // Total cloud cover (coded, see format documentation)
   sky_condition_text text, // Non-coded sky conditions
   one_hour_precip double,   // One-hour accumulated liquid precipitation (millimeters)
   six_hour_precip double,   // Six-hour accumulated liquid precipitation (millimeters)
   PRIMARY KEY ((wsid), year, month, day, hour)
) WITH CLUSTERING ORDER BY (year DESC, month DESC, day DESC, hour DESC);

/*
Quick access lookup table for sky_condition. Useful for potential analytics.
Just in case you want to know what an okta is, here is the wikipedia page:
http://en.wikipedia.org/wiki/Okta
*/

CREATE TABLE IF NOT EXISTS sky_condition_lookup (
    code int PRIMARY KEY,
    condition text
);

CREATE TABLE IF NOT EXISTS daily_aggregate_temperature (
   wsid text,
   year int,
   month int,
   day int,
   high double,
   low double,
   mean double,
   variance double,
   stdev double,
   PRIMARY KEY ((wsid), year, month, day)
) WITH CLUSTERING ORDER BY (year DESC, month DESC, day DESC);

CREATE TABLE IF NOT EXISTS daily_aggregate_windspeed (
   wsid text,
   year int,
   month int,
   day int,
   high double,
   low double,
   mean double,
   variance double,
   stdev double,
   PRIMARY KEY ((wsid), year, month, day)
) WITH CLUSTERING ORDER BY (year DESC, month DESC, day DESC);

CREATE TABLE IF NOT EXISTS daily_aggregate_pressure (
   wsid text,
   year int,
   month int,
   day int,
   high double,
   low double,
   mean double,
   variance double,
   stdev double,
   PRIMARY KEY ((wsid), year, month, day)
) WITH CLUSTERING ORDER BY (year DESC, month DESC, day DESC);

/*
  Sum of all one_hour_precip for one day and one weather station
*/

CREATE TABLE IF NOT EXISTS daily_aggregate_precip (
   wsid text,
   year int,
   month int,
   day int,
   precipitation double,
   PRIMARY KEY ((wsid), year, month, day)
) WITH CLUSTERING ORDER BY (year DESC, month DESC, day DESC);

CREATE TABLE IF NOT EXISTS monthly_aggregate_temperature (
   wsid text,
   year int,
   month int,
   high double,
   low double,
   mean double,
   variance double,
   stdev double,
   PRIMARY KEY ((wsid), year, month)
) WITH CLUSTERING ORDER BY (year DESC, month DESC);

CREATE TABLE IF NOT EXISTS monthly_aggregate_windspeed (
   wsid text,
   year int,
   month int,
   high double,
   low double,
   mean double,
   variance double,
   stdev double,
   PRIMARY KEY ((wsid), year, month)
) WITH CLUSTERING ORDER BY (year DESC, month DESC);

CREATE TABLE IF NOT EXISTS monthly_aggregate_pressure (
   wsid text,
   year int,
   month int,
   high double,
   low double,
   mean double,
   variance double,
   stdev double,
   PRIMARY KEY ((wsid), year, month)
) WITH CLUSTERING ORDER BY (year DESC, month DESC);


CREATE TABLE IF NOT EXISTS monthly_aggregate_precip (
   wsid text,
   year int,
   month int,
   high double,
   low double,
   mean double,
   variance double,
   stdev double,
   PRIMARY KEY ((wsid), year, month)
) WITH CLUSTERING ORDER BY (year DESC, month DESC);

