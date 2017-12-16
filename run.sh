#!/bin/bash

echo $1
java -classpath /home/pi/Development/doorbell-listener/target/classes:/home/pi/.m2/repository/com/pusher/pusher-java-client/1.6.0/pusher-java-client-1.6.0.jar:/home/pi/.m2/repository/com/google/code/gson/gson/2.2.2/gson-2.2.2.jar:/home/pi/.m2/repository/com/pusher/java-websocket/1.4.1/java-websocket-1.4.1.jar doorman.listener.Doorman $1 $2 $3
