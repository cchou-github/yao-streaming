#!/usr/bin/env bash
# Runs inside the LocalStack container once it reports ready.
# Creates the VOD buckets so the app never has to create its own infrastructure
# (in AWS these are owned by Terraform).
set -euo pipefail

RAW_BUCKET="${RAW_BUCKET:-yao-streaming-raw}"
PROCESSED_BUCKET="${PROCESSED_BUCKET:-yao-streaming-processed}"

for bucket in "$RAW_BUCKET" "$PROCESSED_BUCKET"; do
  awslocal s3 mb "s3://$bucket" 2>/dev/null || echo "bucket $bucket already exists"
done

# The browser fetches HLS manifests and segments directly, and loads them
# cross-origin from the app on :8080, so the raw bucket needs to accept
# presigned PUTs from that origin and the processed bucket needs to be readable.
awslocal s3api put-bucket-cors --bucket "$RAW_BUCKET" --cors-configuration '{
  "CORSRules": [{
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedOrigins": ["*"],
    "ExposeHeaders": ["ETag"]
  }]
}'

awslocal s3api put-bucket-cors --bucket "$PROCESSED_BUCKET" --cors-configuration '{
  "CORSRules": [{
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["GET", "HEAD"],
    "AllowedOrigins": ["*"]
  }]
}'

# Local-only: lets the browser play processed output without presigning every
# segment. The deployed equivalent is CloudFront in front of a private bucket.
awslocal s3api put-bucket-policy --bucket "$PROCESSED_BUCKET" --policy "{
  \"Version\": \"2012-10-17\",
  \"Statement\": [{
    \"Effect\": \"Allow\",
    \"Principal\": \"*\",
    \"Action\": \"s3:GetObject\",
    \"Resource\": \"arn:aws:s3:::$PROCESSED_BUCKET/*\"
  }]
}"

echo "buckets ready: $RAW_BUCKET, $PROCESSED_BUCKET"
