-- KEYS[1] = geo set of online drivers, KEYS[2] = hash driverId -> rideId of busy drivers
-- ARGV[1] = driverId, ARGV[2] = rideId
-- Returns 1 when the driver is now reserved for this ride, 0 when busy with another ride, -1 when offline.
if redis.call('ZSCORE', KEYS[1], ARGV[1]) == false then
  return -1
end
if redis.call('HSETNX', KEYS[2], ARGV[1], ARGV[2]) == 1 then
  return 1
end
if redis.call('HGET', KEYS[2], ARGV[1]) == ARGV[2] then
  return 1 -- idempotent retry of the same reservation
end
return 0
