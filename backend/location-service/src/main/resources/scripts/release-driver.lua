-- KEYS[1] = hash driverId -> rideId, ARGV[1] = driverId, ARGV[2] = rideId
-- Only releases the driver if they are still held by this ride (compare-and-delete).
if redis.call('HGET', KEYS[1], ARGV[1]) == ARGV[2] then
  return redis.call('HDEL', KEYS[1], ARGV[1])
end
return 0
