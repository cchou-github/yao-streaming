# "yao-streaming-raw" alone is generic enough it may already be taken in
# S3's global bucket namespace. Suffixing with the account ID (deterministic,
# not random) avoids a first-apply BucketAlreadyExists failure. Consequence:
# these won't match the app's built-in property defaults - the follow-up
# deploy step must set RAW_BUCKET/PROCESSED_BUCKET from this module's
# outputs, the same env-var-driven pattern k8s/app.yaml already uses.
locals {
  raw_bucket_name       = var.raw_bucket_name != "" ? var.raw_bucket_name : "${var.project_name}-raw-${data.aws_caller_identity.current.account_id}"
  processed_bucket_name = var.processed_bucket_name != "" ? var.processed_bucket_name : "${var.project_name}-processed-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket" "raw" {
  bucket = local.raw_bucket_name

  # Without this, `terraform destroy` fails outright on a non-empty bucket
  # (the S3 API refuses to delete one) and needs a manual empty first.
  # Accepted tradeoff: any uploaded video is gone for good on destroy, no
  # recovery - fine for a project that's meant to be destroyed and rebuilt
  # freely between sessions, per user's explicit call.
  force_destroy = true

  tags = {
    Name = local.raw_bucket_name
  }
}

resource "aws_s3_bucket" "processed" {
  bucket = local.processed_bucket_name

  # See aws_s3_bucket.raw's force_destroy comment - same reasoning.
  force_destroy = true

  tags = {
    Name = local.processed_bucket_name
  }
}

# BucketOwnerEnforced disables ACLs entirely (modern best practice).
# Doesn't affect presigned URLs, which are SigV4-signed authenticated-
# principal requests, not ACL/"public" access.
resource "aws_s3_bucket_ownership_controls" "raw" {
  bucket = aws_s3_bucket.raw.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_ownership_controls" "processed" {
  bucket = aws_s3_bucket.processed.id
  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# Both buckets fully block public access. This is the key divergence from
# local dev: the processed bucket gets no bucket policy at all here (see
# below) - the architecture diagram calls the local public-read policy out
# explicitly as a LocalStack-only shortcut. The real processed bucket is
# meant to sit behind CloudFront with Origin Access Control, which is a
# later step - so it's correctly private-and-unused for now. Block Public
# Access only blocks anonymous access, so presigned GET/PUT still work
# fine against a fully private bucket.
resource "aws_s3_bucket_public_access_block" "raw" {
  bucket = aws_s3_bucket.raw.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_public_access_block" "processed" {
  bucket = aws_s3_bucket.processed.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# No bucket policy defined here - the OAC-scoped policy granting
# cloudfront.amazonaws.com GetObject, conditioned on the distribution
# ARN, now lives in cloudfront.tf (aws_s3_bucket_policy.processed_cloudfront_oac)
# since it references the distribution. Do NOT restore the old
# public-read policy.

# Mirrors localstack/init/01-create-buckets.sh's raw bucket CORS exactly.
resource "aws_s3_bucket_cors_configuration" "raw" {
  bucket = aws_s3_bucket.raw.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["PUT", "GET", "HEAD"]
    allowed_origins = var.bucket_cors_allowed_origins
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

resource "aws_s3_bucket_cors_configuration" "processed" {
  bucket = aws_s3_bucket.processed.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "HEAD"]
    allowed_origins = var.bucket_cors_allowed_origins
    max_age_seconds = 3000
  }
}

# Abandoned browser uploads (closed tab mid-upload) shouldn't bill forever.
resource "aws_s3_bucket_lifecycle_configuration" "raw" {
  bucket = aws_s3_bucket.raw.id

  rule {
    id     = "abort-incomplete-multipart-uploads"
    status = "Enabled"

    # Empty filter = applies to every object in the bucket, not just a
    # prefix. AWS's API requires one or the other explicitly.
    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "processed" {
  bucket = aws_s3_bucket.processed.id

  rule {
    id     = "abort-incomplete-multipart-uploads"
    status = "Enabled"

    # Empty filter = applies to every object in the bucket, not just a
    # prefix. AWS's API requires one or the other explicitly.
    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}
