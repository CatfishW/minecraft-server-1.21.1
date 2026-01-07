#!/bin/bash
cd "$(dirname "$0")"
java -Xmx10G -Xms10G -jar fabric-server-launch.jar nogui
