#!/bin/bash

value=${SPECMATIC_REQUEST:20:1}

cat << EOF
{
    "status": 200,
    "body": $((value * 3)),
    "status-text": "OK",
    "headers": {
        "X-Specmatic-Result": "success",
        "Content-Type": "text/plain"
    }
}
EOF
