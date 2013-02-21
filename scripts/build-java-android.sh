#!/bin/sh

set -e

BUILD_NUMBER=$1
ORIGINAL_PWD=`pwd`
ROOT=$(cd ..; pwd)

die()
{
    echo >&2 "$@"
    exit
}

[ "$#" -ge 1  ] || die "Usage: $0 BUILD_NUMBER"

killed()
{
  cd $ORIGINAL_PWD
  exit 1
}

trap killed SIGINT SIGTERM SIGKILL

ensure_command()
{
  type $@ >/dev/null 2>&1 || { echo >&2 "No such command: $@. Android SDK in PATH?"; exit 1; }
}

precheck()
{
  for cmd in android adb emulator ant
  do
    ensure_command $cmd
  done
}

stop_emulator()
{
  echo "Stopping emulator (if exists)"
  adb emu kill || echo "Failed to stop emulator"
}

ensure_emulator()
{
  if (ps aux | grep '[e]mulator64-x86'); then
    echo "Emulator seems to be running already."
    return
  fi

  echo "No emulator found. Starting one..."

  adb start-server
  device=$(android list avd -c)

  echo "Booting AVD $device..."
  emulator -avd $device &

  # NORMALLY: adb wait-for-device
  # but this commands hangs somtimes
  echo "Waiting until device has booted..."
  while [ `adb shell 'getprop dev.bootcomplete'` != 1 ]; do
    echo "Waiting..."
    sleep 1
  done

  echo "Emulator ready!"
}

run_tests()
{
  echo "Building java-android..."

  pushd `pwd`
  cd source/java-android
  ant debug
  popd

  echo "Building java-android... [DONE]"

  echo "Building test app and run tests..."

  pushd `pwd`
  cd tests/android/tests

  adb wait-for-device
  ant emma debug
  ant emma installt test
  popd

  echo "Building test app and run tests [DONE]"
}

precheck
pushd `pwd`
cd $ROOT
# stop_emulator
ensure_emulator
run_tests
popd
