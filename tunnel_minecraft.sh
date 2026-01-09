#!/bin/bash
# Reverse SSH Tunnel for Minecraft Server with socat forwarding

REMOTE_USER="lobin"
REMOTE_HOST="vpn.agaii.org"
LOCAL_PORT=25565
TUNNEL_PORT=25555
PUBLIC_PORT=25565
RECONNECT_DELAY=5

# Resolve IP from domain
REMOTE_IP=$(dig +short "$REMOTE_HOST" | head -n1)

echo "=== Minecraft Server Reverse SSH Tunnel ==="
echo "Domain: $REMOTE_HOST"
echo "Resolved IP: ${REMOTE_IP:-'(could not resolve)'}"
echo ""
echo "Players can connect using:"
echo "  - $REMOTE_HOST:$PUBLIC_PORT"
[[ -n "$REMOTE_IP" ]] && echo "  - $REMOTE_IP:$PUBLIC_PORT"
echo ""

# Check if Minecraft server is running locally
if nc -z 127.0.0.1 $LOCAL_PORT 2>/dev/null; then
    echo "[OK] Minecraft server is running on port $LOCAL_PORT"
else
    echo "[WARN] Minecraft server not responding on port $LOCAL_PORT"
fi

echo ""

# Main loop
ATTEMPT=0
while true; do
    ((ATTEMPT++))
    echo "[$(date +%H:%M:%S)] Starting tunnel (attempt #$ATTEMPT)..."
    
    # Kill any existing socat on remote
    ssh $REMOTE_USER@$REMOTE_HOST "pkill -f 'socat.*$PUBLIC_PORT'" 2>/dev/null
    
    # Start SSH tunnel with remote command to run socat
    ssh -R $TUNNEL_PORT:127.0.0.1:$LOCAL_PORT \
        -o ServerAliveInterval=30 \
        -o ServerAliveCountMax=3 \
        -o ExitOnForwardFailure=yes \
        -o StrictHostKeyChecking=no \
        $REMOTE_USER@$REMOTE_HOST \
        "echo 'Starting socat forwarder...'; socat TCP-LISTEN:$PUBLIC_PORT,fork,reuseaddr TCP:127.0.0.1:$TUNNEL_PORT"
    
    EXIT_CODE=$?
    [[ $EXIT_CODE -eq 0 ]] && break
    
    echo "[WARN] Disconnected (exit code: $EXIT_CODE). Reconnecting in ${RECONNECT_DELAY}s..."
    sleep $RECONNECT_DELAY
done
