# doorbell-listener

Doorbell listener provides a service that runs on a Rasberry Pi
and invokes commands when transmitted Pusher.

The Doorbell listener should subscribe to a Pusher channel, when an
event is triggered, verify the request and the execute the command defined
in the doorbell.properties

doorbell.properties should be updated to include the values provided at:
pusher.com API keys.
