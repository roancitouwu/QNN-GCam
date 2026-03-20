#!/bin/bash
# Setup Remote ADB Debugging via Cloudflare Tunnel
# Run this on the VM to allow remote ADB connections

set -e

echo '=== Installing Cloudflare Tunnel ==='
if ! command -v cloudflared &> /dev/null; then
    curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o /usr/local/bin/cloudflared
    chmod +x /usr/local/bin/cloudflared
    echo 'Cloudflared installed'
fi

echo '=== Setting up ADB ==='
export ANDROID_HOME=/opt/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools

# Start ADB in TCP mode
adb start-server
adb tcpip 5555

echo ''
echo '=== Starting Cloudflare Tunnel ==='
echo 'This will create a public URL for ADB access'
echo ''
echo 'On your LOCAL machine, run:'
echo '  cloudflared access tcp --hostname <TUNNEL_URL> --url localhost:5555'
echo '  adb connect localhost:5555'
echo ''
cloudflared tunnel --url tcp://localhost:5555
