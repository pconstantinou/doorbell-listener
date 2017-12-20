# doorbell-listener

See https://medium.com/@constantinou/can-someone-buzz-me-in-850fd40efee7 for more detail.

Doorbell listener provides a service that runs on a Rasberry Pi
and invokes commands when transmitted Pusher.

The Doorbell listener should subscribe to a Pusher channel, when an
event is triggered, verify the request and the execute the command defined
in the doorbell.properties

doorbell.properties should be updated to include the values provided at:
pusher.com API keys.

doorbell.properties -> commannd property is the shell script that's executed by the Java app if a valid password is transmittied

# Setup

* Go to pusher.com and create a app 
* Add the API keys to doorbell.properties
* Create passwords.properties with the key as the keycode and the value as the name of the owner (which is logged)
```
mvn install
```
to build the project 
```
./run.sh doorbell.properties passwords.properties
``` to start the script
* Using the Debug console on pusher.com, push valid and invalid passcodes (in the JSON message attribute) to valide the configuration. For example:
```
{
message: "8675309"
}
```
* One you're confident that the connection from pusher.com to your Pi is set up, modify the command attribute to togging the Pi's GPIOs to trigger the doorlock. Checkout this and other videos: https://www.youtube.com/watch?v=OQyntQLazMU



