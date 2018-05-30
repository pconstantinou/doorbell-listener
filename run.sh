#!/bin/bash

set -x

CP=/home/pi/Development/doorbell-listener/target/classes:/home/pi/.m2/repository/com/pusher/pusher-java-client/1.8.0/pusher-java-client-1.8.0.jar:/home/pi/.m2/repository/com/google/code/gson/gson/2.2.2/gson-2.2.2.jar:/home/pi/.m2/repository/com/pusher/java-websocket/1.4.1/java-websocket-1.4.1.jar
echo $1 $2
while true
do
    java -classpath /home/pi/Development/doorbell-listener/target/classes:/home/pi/.m2/repository/com/pusher/pusher-java-client/1.8.0/pusher-java-client-1.8.0.jar:/home/pi/.m2/repository/com/google/code/gson/gson/2.2.2/gson-2.2.2.jar:/home/pi/.m2/repository/com/pusher/java-websocket/1.4.1/java-websocket-1.4.1.jar doorman.listener.Doorman $1 $2 $3 |  rotatelogs /home/pi/Development/doorbell-listener/logs/doorman 10K &
    pid=$!
    echo $pid > doorman.pid
    sleep 10
    while true
    do
	x=`ping -c1 google.com 2>&1 | grep unknown`
	if [ ! "$x" = "" ]; then
	    echo "It's down!! Attempting to restart."
	    kill $pid
	    break
	fi
	sleep 120
    done    
done
