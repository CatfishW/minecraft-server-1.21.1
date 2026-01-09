#!/bin/bash
cd "$(dirname "$0")"
java -Xmx50G -Xms50G -jar fabric-server-launch.jar nogui
