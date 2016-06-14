#!/bin/bash

#usage: $0 [start|stop|status]

if [ -e /etc/default/ike ]; then
	. /etc/default/ike
else
	echo "Missing config file: /etc/default/ike. Contact your administrator"
	exit 1
fi

if [ $UID -eq 0 ]; then
	echo "Do not run as root. exiting"
	exit 1
fi

# vars defined in /etc/default/ike:
# START - used by rc.d to determine whether $0 should be invoked on system startup
# APPROOT - fully qualified path to install location
# CREDENTIALS - path to postgres pw file

#user defined variables
CLASS_NAME="org.allenai.ike.IkeToolWebapp"
#CLASS_PATH is determined programmatically below in start()
JVM_ARGS="-Xmx60G -Xms60G"
prog="ike"
LOGBACK_APP="$prog"
LOGBACK_CONF="$APPROOT/conf/logback.xml"
CONF_FILE="$APPROOT/conf/env.conf"

ike_action=$1

exec="/usr/bin/java"
SCRIPT_DIR="$APPROOT/bin"
pidfile=$APPROOT/${prog}.pid
stdoutfile=$APPROOT/${prog}.out
stderrfile=$APPROOT/${prog}.err

LOGBACK_ARGS="-Dlogback.appname=$LOGBACK_APP -Dlogback.configurationFile=$LOGBACK_CONF"
CONF_ARGS="-Dconfig.file=$CONF_FILE"

start() {
	status > /dev/null
	if [ $? -eq 0 ]; then
		echo "$0 is already running."
		return 0
	fi

	if [ -e "$CREDENTIALS" ]; then
		source "$CREDENTIALS"
	else
		echo "Error: $CREDENTIALS not found; Should be defined in /etc/default/$prod. $0 will fail to start."
	fi

   cd $APPROOT
   CLASS_PATH=`find lib -name '*.jar' | tr "\\n" :`
   JAVA_ARGS="$JVM_ARGS -classpath $CLASS_PATH $CONF_ARGS $LOGBACK_ARGS"

   # --- start the program ---
   echo -n "Starting $prog: "
   nohup $exec $JAVA_ARGS $CLASS_NAME > "$stdoutfile" 2> "$stderrfile" &
   retval=$?
   echo

   sleep 2

   ps -p $! > /dev/null
   if [ $? -eq 0 ]; then
      echo $! > "$pidfile"
   fi

   return $retval
}

stop() {
	if ! [ -e $pidfile ]; then
		echo "pidfile: $pidfile not found. Assumption: $prog is NOT running"
		return 0
	fi	
	kill `cat "$pidfile"` > /dev/null
	retval=$?
	rm $pidfile
	return $retval
}

# returns 0 if running; 1 or 2 if not
status() {
	if ! [ -e $pidfile ]; then
		echo "pidfile: $pidfile not found. Assumption: $prog is NOT running"
		return 2
	fi	
	ps -p `cat "$pidfile"` > /dev/null
	if [ $? -eq 0 ]; then
		echo "$prog is running"
		return 0
	else
		echo "$prog is NOT running"
		return 1
	fi
}

case "$1" in 
	start)
		$1
	;;
	stop)
		$1
	;;
	status)
		$1
	;;
	*)
		echo "Unsupported Input: $1. expected: start|stop|status"
	;;
esac
