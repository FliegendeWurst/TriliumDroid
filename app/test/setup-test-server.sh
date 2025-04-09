#!/usr/bin/env bash

export HOME=$(mktemp -d)
export TRILIUM_PORT=${TRILIUM_PORT:-8080}
trilium-server & disown
sleep ${SETUP_SLEEP:-10}
curl "http://localhost:$TRILIUM_PORT/api/setup/new-document" -X POST
curl "http://localhost:$TRILIUM_PORT/set-password" -X POST --data-raw "password1=1234&password2=1234"
