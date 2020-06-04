#!/bin/bash

curl -d "client_id=client1" -d "client_secret=31eb0040-4b2c-4512-9d1f-1c418b362120" \
    -d "username=user1@somewhere.com" -d "password=user1" \
    -d "grant_type=password" \
    "http://localhost:8000/auth/realms/sipa/protocol/openid-connect/token"
