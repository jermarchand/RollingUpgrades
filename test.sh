#!/bin/sh

CLIENT_SECRET=31eb0040-4b2c-4512-9d1f-1c418b362120
KC_REALM=sipa

KC_ACCESS_TOKEN=$(curl -d "client_id=client1" -d "client_secret=$CLIENT_SECRET" \
    -d "username=user1@somewhere.com" -d "password=user1" \
    -d "grant_type=password" \
    "http://localhost:8000/auth/realms/$KC_REALM/protocol/openid-connect/token" |jq -r .access_token)

echo $KC_ACCESS_TOKEN

for i in {1..10}; do
    echo "--------------------------------------------------------------------------"
    echo "User userinfo"
    curl -X POST \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "access_token=$KC_ACCESS_TOKEN" \
        "http://localhost:8000/auth/realms/$KC_REALM/protocol/openid-connect/userinfo" | jq .

done
