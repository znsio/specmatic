#!/bin/bash

multiplier=$1
value=${SPECMATIC_REQUEST:20:1}

cat << EOF
{
    "status": 200,
    "body": $((value * multiplier)),
    "status-text": "OK",
    "headers": {
        "X-Specmatic-Result": "success",
        "Content-Type": "text/plain"
    }
}
EOF
