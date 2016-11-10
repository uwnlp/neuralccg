#!/bin/bash

if (( $# < 2 )); then
    echo "Usage: $0 <config> <goal> [port]"
    exit 1
fi

mvn compile exec:exec -Dconf=$1 -Dgoal=${2} -Dport=${3:-8080}
