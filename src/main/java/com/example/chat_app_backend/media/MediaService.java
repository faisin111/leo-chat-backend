package com.example.chat_app_backend.media;

import com.example.chat_app_backend.chat.message.MessageAttachmentRepository;
import com.example.chat_app_backend.common.exception.BusinessRuleException;
import com.example.chat_app_backend.config.storage.SupabaseStorageProperties;
import com.example.chat_app_backend.media.dto.MediaConfirmRequest;
import com.example.chat_app_backend.media.dto.MediaPresignRequest;
import com.example.chat_app_backend.user.ProfileRepository;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class MediaService {

  private static final Logger log = LoggerFactory.getLogger(MediaService.class);

  private static final Map<String, String> SUPPORTED_EXTENSIONS =
      Map.ofEntries(
          Map.entry("image/jpeg", ".jpg"),
          Map.entry("image/png", ".png"),
          Map.entry("image/webp", ".webp"),
          Map.entry("image/gif", ".gif"),
          Map.entry("image/avif", ".avif"),
          Map.entry("application/pdf", ".pdf"),
          Map.entry("application/msword", ".doc"),
          Map.entry(
              "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),
          Map.entry("application/vnd.ms-excel", ".xls"),
          Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
          Map.entry("application/vnd.ms-powerpoint", ".ppt"),
          Map.entry(
              "application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx"),
          Map.entry("text/plain", ".txt"),
          Map.entry("text/csv", ".csv"),
          Map.entry("application/zip", ".zip"),
          Map.entry("application/x-zip-compressed", ".zip"),
          Map.entry("application/json", ".json"),
          Map.entry("audio/mpeg", ".mp3"),
          Map.entry("audio/wav", ".wav"),
          Map.entry("audio/webm", ".webm"),
          Map.entry("audio/ogg", ".ogg"),
          Map.entry("video/mp4", ".mp4"),
          Map.entry("video/webm", ".webm"),
          Map.entry("application/octet-stream", ".bin"));

  private final ObjectProvider<S3Presigner> presigner;
  private final ObjectProvider<S3Client> client;
  private final SupabaseStorageProperties properties;
  private final MessageAttachmentRepository attachmentRepository;
  private final ProfileRepository profileRepository;

  public MediaService(
      ObjectProvider<S3Presigner> presigner,
      ObjectProvider<S3Client> client,
      SupabaseStorageProperties properties,
      MessageAttachmentRepository attachmentRepository,
      ProfileRepository profileRepository) {
    this.presigner = presigner;
    this.client = client;
    this.properties = properties;
    this.attachmentRepository = attachmentRepository;
    this.profileRepository = profileRepository;
  }

  public Map<String, Object> presign(UUID userId, MediaPresignRequest request) {
    ensureEnabled();
    String mimeType = normalizeMimeType(request.mimeType());
    if (!SUPPORTED_EXTENSIONS.containsKey(mimeType)) {
      throw new BusinessRuleException("Unsupported file type");
    }
    if (request.sizeInBytes() < 1 || request.sizeInBytes() > properties.maxImageBytes()) {
      throw new BusinessRuleException("File exceeds the configured size limit");
    }

    String storageKey =
        "users/" + userId + "/" + UUID.randomUUID() + SUPPORTED_EXTENSIONS.get(mimeType);
    PutObjectRequest putRequest =
        PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(storageKey)
            .contentType(mimeType)
            .contentLength(request.sizeInBytes())
            .build();
    PresignedPutObjectRequest signed =
        presigner
            .getObject()
            .presignPutObject(
                PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(properties.uploadExpirySeconds()))
                    .putObjectRequest(putRequest)
                    .build());

    return Map.of(
        "bucket", properties.bucket(),
        "storageKey", storageKey,
        "uploadUrl", signed.url().toString(),
        "method", "PUT",
        "contentType", mimeType,
        "sizeInBytes", request.sizeInBytes(),
        "expiresIn", properties.uploadExpirySeconds());
  }

  public Map<String, Object> confirm(UUID userId, MediaConfirmRequest request) {
    ensureEnabled();
    String storageKey = requireOwnedKey(userId, request.storageKey());
    try {
      var metadata =
          client
              .getObject()
              .headObject(
                  HeadObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build());
      if (metadata.contentLength() == null || metadata.contentLength() < 1) {
        throw new BusinessRuleException("Uploaded file is empty");
      }
      if (metadata.contentLength() > properties.maxImageBytes()) {
        throw new BusinessRuleException("Uploaded file exceeds the configured size limit");
      }
      String contentType = normalizeMimeType(metadata.contentType());
      if (!SUPPORTED_EXTENSIONS.containsKey(contentType)) {
        throw new BusinessRuleException("Uploaded object is not a supported file type");
      }
      GetObjectRequest getRequest =
          GetObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build();
      PresignedGetObjectRequest signed =
          presigner
              .getObject()
              .presignGetObject(
                  GetObjectPresignRequest.builder()
                      .signatureDuration(Duration.ofSeconds(properties.downloadExpirySeconds()))
                      .getObjectRequest(getRequest)
                      .build());
      return Map.of(
          "bucket", properties.bucket(),
          "storageKey", storageKey,
          "url", signed.url().toString(),
          "contentType", contentType,
          "sizeInBytes", metadata.contentLength(),
          "expiresIn", properties.downloadExpirySeconds());
    } catch (NoSuchKeyException e) {
      throw new BusinessRuleException("Upload is not complete");
    } catch (S3Exception e) {
      if (e.statusCode() == 404) throw new BusinessRuleException("Upload is not complete");
      throw new BusinessRuleException("Storage provider rejected the request");
    } catch (SdkException e) {
      throw new BusinessRuleException("Storage provider is unavailable");
    }
  }

  public Map<String, Object> downloadUrl(String storageKey) {
    ensureEnabled();
    if (storageKey == null
        || !storageKey.equals(storageKey.trim())
        || !storageKey.startsWith("users/")
        || storageKey.length() > 300
        || storageKey.contains("..")) {
      throw new BusinessRuleException("Invalid storage key");
    }
    GetObjectRequest getRequest =
        GetObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build();
    try {
      PresignedGetObjectRequest signed =
          presigner
              .getObject()
              .presignGetObject(
                  GetObjectPresignRequest.builder()
                      .signatureDuration(Duration.ofSeconds(properties.downloadExpirySeconds()))
                      .getObjectRequest(getRequest)
                      .build());
      return Map.of(
          "bucket", properties.bucket(),
          "storageKey", storageKey,
          "url", signed.url().toString(),
          "expiresIn", properties.downloadExpirySeconds());
    } catch (SdkException e) {
      throw new BusinessRuleException("Storage provider is unavailable");
    }
  }

  public String assertOwnedImage(UUID userId, String storageKey) {
    ensureEnabled();
    String ownedKey = requireOwnedKey(userId, storageKey);
    try {
      var metadata =
          client
              .getObject()
              .headObject(
                  HeadObjectRequest.builder().bucket(properties.bucket()).key(ownedKey).build());
      String contentType = normalizeMimeType(metadata.contentType());
      if (!contentType.startsWith("image/")
          || metadata.contentLength() == null
          || metadata.contentLength() < 1
          || metadata.contentLength() > properties.maxImageBytes()) {
        throw new BusinessRuleException("Profile picture is invalid");
      }
      return ownedKey;
    } catch (NoSuchKeyException e) {
      throw new BusinessRuleException("Profile picture upload is not complete");
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        throw new BusinessRuleException("Profile picture upload is not complete");
      }
      throw new BusinessRuleException("Storage provider rejected the request");
    } catch (SdkException e) {
      throw new BusinessRuleException("Storage provider is unavailable");
    }
  }

  public String profilePictureUrl(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) return null;
    return String.valueOf(downloadUrl(storageKey).get("url"));
  }

  public void deleteOwnedObject(UUID userId, String storageKey) {
    ensureEnabled();
    String ownedKey = requireOwnedKey(userId, storageKey);
    if (attachmentRepository.existsByStorageKey(ownedKey)
        || profileRepository.existsByUserIdAndProfilePictureStorageKey(userId, ownedKey)) {
      throw new BusinessRuleException("Media is still in use");
    }
    deleteObject(ownedKey);
  }

  public void deleteProfilePicture(UUID userId, String storageKey) {
    ensureEnabled();
    deleteObject(requireOwnedKey(userId, storageKey));
  }

  /** Best-effort cleanup for attachment objects after their database references are deleted. */
  public void deleteConversationObjects(List<String> storageKeys) {
    if (storageKeys == null || storageKeys.isEmpty() || client.getIfAvailable() == null) return;
    for (String storageKey : storageKeys) {
      if (storageKey == null
          || !storageKey.startsWith("users/")
          || storageKey.length() > 300
          || storageKey.contains("..")) {
        log.warn("Skipping invalid conversation attachment storage key during cleanup");
        continue;
      }
      try {
        deleteObject(storageKey);
      } catch (BusinessRuleException e) {
        log.warn("Could not delete conversation attachment object {}", storageKey, e);
      }
    }
  }

  private void deleteObject(String storageKey) {
    try {
      client
          .getObject()
          .deleteObject(
              DeleteObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build());
    } catch (SdkException e) {
      throw new BusinessRuleException("Storage provider is unavailable");
    }
  }

  private void ensureEnabled() {
    if (!properties.enabled()
        || presigner.getIfAvailable() == null
        || client.getIfAvailable() == null) {
      throw new BusinessRuleException("Storage is not configured");
    }
  }

  private String requireOwnedKey(UUID userId, String storageKey) {
    if (storageKey == null || !storageKey.equals(storageKey.trim())) {
      throw new BusinessRuleException("Invalid storage key");
    }
    String prefix = "users/" + userId + "/";
    if (!storageKey.startsWith(prefix) || storageKey.length() > 180 || storageKey.contains("..")) {
      throw new BusinessRuleException("Storage key does not belong to this user");
    }
    return storageKey;
  }

  private String normalizeMimeType(String mimeType) {
    if (mimeType == null) return "";
    // Browsers commonly send values such as audio/webm;codecs=opus.
    // Storage validation is based on the media type, not optional parameters.
    return mimeType.trim().toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
  }
}
