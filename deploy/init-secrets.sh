#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
umask 077
mkdir -p .wealthflow/docker
chmod 700 .wealthflow/docker
key=.wealthflow/docker/master-key.txt
if [ ! -e "$key" ]; then
    (set -C; openssl rand -base64 32 > "$key")
fi
if [ "$(openssl base64 -d -in "$key" | wc -c | tr -d ' ')" != 32 ]; then
    echo 'Invalid master key. Restore the original key; do not overwrite it for an existing database.' >&2
    exit 1
fi
# The host directory is private; the container's non-root user must read the mounted secret.
chmod 444 "$key"
echo 'Docker master key is ready (never commit or replace it).'
