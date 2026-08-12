#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ ! -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  if command -v gradle >/dev/null 2>&1; then
    gradle -p "$APP_HOME" wrapper --gradle-version 9.1.0 --distribution-type bin
  else
    echo "Gradle is not installed. Run this project in Android Studio or install Gradle 9.1+ first." >&2
    exit 1
  fi
fi
exec java -Dorg.gradle.appname=gradlew -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
