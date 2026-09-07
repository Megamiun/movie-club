package br.com.gabryel.movieclub.service.storage

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.net.URI
import kotlin.uuid.Uuid

/** Thin wrapper around the AWS S3 SDK -- the first real use of the `poster_s3_key`-style storage this app has long
 * had columns for but never actually wired up (posters are served straight from TMDB's own CDN instead, see
 * CLAUDE.md's MediaItem section). [endpointUrl] is set only for local dev against MinIO (see docker-compose.yml);
 * left unset, this talks to real AWS S3 with its default endpoint. */
class S3StorageClient(
    accessKeyId: String? = null,
    secretAccessKey: String? = null,
    private val region: String,
    private val bucketName: String,
    endpointUrl: String? = null,
    private val publicBaseUrl: String? = null,
) {
    private val client: S3Client = S3Client.builder()
        .region(Region.of(region))
        .also { builder ->
            // Explicit keys are only ever set for local dev against MinIO (see docker-compose.yml/.env.example --
            // MinIO has no IAM/instance-metadata concept of its own to fall back to). Left unset, this defaults to
            // the SDK's own DefaultCredentialsProvider chain, which resolves a real deployment's EC2 instance role
            // automatically (env vars, instance metadata, etc.) -- passing blank strings to StaticCredentialsProvider
            // instead threw at construction time, during application boot, which took the entire app down in
            // production (not just photo uploads) the moment this class was first wired into Application.kt, since
            // no AWS keys are configured there at all.
            builder.credentialsProvider(
                if (!accessKeyId.isNullOrBlank() && !secretAccessKey.isNullOrBlank()) {
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))
                } else {
                    DefaultCredentialsProvider.builder().build()
                },
            )
        }
        .also { builder ->
            if (!endpointUrl.isNullOrBlank()) {
                builder.endpointOverride(URI.create(endpointUrl))
                // MinIO (and most non-AWS S3-compatible stores) don't support AWS's own virtual-hosted-style
                // bucket addressing (`bucket.host/key`) -- path-style (`host/bucket/key`) is what actually works.
                builder.forcePathStyle(true)
            }
        }
        .build()

    /** Uploads [bytes] under a fresh, unguessable key inside [keyPrefix] and returns that key (not a URL -- see
     * [publicUrlFor], kept separate since the bucket/CDN domain can change independently of stored data).
     * Lazily creates the bucket first so local dev against a fresh MinIO container (no pre-provisioned bucket)
     * works without a separate setup step; a real deployment's bucket already exists, so that check is a no-op. */
    fun upload(keyPrefix: String, bytes: ByteArray, contentType: String): String {
        ensureBucketExists()
        val key = "$keyPrefix/${Uuid.random()}"
        client.putObject(
            PutObjectRequest.builder().bucket(bucketName).key(key).contentType(contentType).build(),
            RequestBody.fromBytes(bytes),
        )
        return key
    }

    /** [publicBaseUrl] set (local MinIO) means path-style (`base/bucket/key` -- MinIO has no per-bucket subdomain
     * of its own); unset (real AWS) means the standard virtual-hosted form (`bucket.s3.region.amazonaws.com/key`,
     * bucket already in the host). */
    fun publicUrlFor(key: String): String {
        val base = publicBaseUrl?.trimEnd('/')
        return if (base != null) "$base/$bucketName/$key" else "https://$bucketName.s3.$region.amazonaws.com/$key"
    }

    /** Also applies a public-read bucket policy -- a photo needs to load directly in an `<img src>` from the
     * browser, and a freshly-created bucket (MinIO or real S3) defaults to private. Real S3 accounts with "Block
     * Public Access" enabled (the modern default) will reject this; that's a deliberate account-level guard this
     * app doesn't try to work around; it only actually matters once real AWS credentials are configured. */
    private fun ensureBucketExists() {
        try {
            client.headBucket { it.bucket(bucketName) }
        } catch (e: S3Exception) {
            if (e.statusCode() != 404) throw e
            client.createBucket { it.bucket(bucketName) }
            client.putBucketPolicy { it.bucket(bucketName).policy(publicReadPolicy(bucketName)) }
        }
    }

    private fun publicReadPolicy(bucket: String) =
        """
        {
          "Version": "2012-10-17",
          "Statement": [{
            "Effect": "Allow",
            "Principal": "*",
            "Action": "s3:GetObject",
            "Resource": "arn:aws:s3:::$bucket/*"
          }]
        }
        """.trimIndent()
}
