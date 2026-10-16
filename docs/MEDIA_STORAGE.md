# Supabase image storage

`POST /api/v1/media/presign` returns `422` with `Image storage is not configured` until all storage settings exist in the backend deployment.

Set these Render environment variables, then redeploy:

```env
SUPABASE_STORAGE_ENABLED=true
SUPABASE_STORAGE_ENDPOINT=https://lzkeptvooqgodjbngsyh.storage.supabase.co/storage/v1/s3
SUPABASE_STORAGE_REGION=ap-southeast-1
SUPABASE_STORAGE_ACCESS_KEY=<Supabase Storage S3 access key>
SUPABASE_STORAGE_SECRET_KEY=<Supabase Storage S3 secret key>
SUPABASE_STORAGE_BUCKET=images
SUPABASE_STORAGE_UPLOAD_EXPIRY_SECONDS=900
SUPABASE_STORAGE_DOWNLOAD_EXPIRY_SECONDS=900
SUPABASE_STORAGE_MAX_IMAGE_BYTES=10485760
```

Create a private `images` bucket first. Use Supabase Storage S3 Access Keys. The PostgreSQL password is not an S3 access key and cannot sign upload URLs.

## Upload flow

1. `POST /api/v1/media/presign` with `{ "mimeType": "image/png", "sizeInBytes": 12345 }`.
2. `PUT` the bytes to the returned `uploadUrl` with the returned `Content-Type`.
3. `POST /api/v1/media/confirm` with `{ "storageKey": "users/<user-id>/<key>.png" }`.

Supported types: JPEG, PNG, WebP, GIF, AVIF. Maximum size: 10 MiB.

## Verification

After redeploy, send an authenticated request:

```bash
curl -i -X POST https://leo-chat-backend.onrender.com/api/v1/media/presign \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  --data '{"mimeType":"image/png","sizeInBytes":12345}'
```

Expected response: `200` with `uploadUrl`, `storageKey`, `contentType`, and `expiresIn`.

Expected configuration errors:

```json
{
  "status": 422,
  "code": "BUSINESS_RULE_VIOLATION",
  "message": "Image storage is not configured"
}
```
