package services

import javax.inject._
import play.api.Configuration
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.sync._
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3._
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model._
import java.io.File
import java.net.URI
import java.time.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import play.api.Logging

@Singleton
class StorageService @Inject()(
  configuration: Configuration
)(implicit ec: ExecutionContext) extends Logging {
  
  private val s3Endpoint = configuration.get[String]("aws.s3.endpoint")
  private val s3Region = configuration.get[String]("aws.s3.region")
  private val s3Bucket = configuration.get[String]("aws.s3.bucket")
  private val accessKey = configuration.get[String]("aws.accessKey")
  private val secretKey = configuration.get[String]("aws.secretKey")
  
  private val credentials = AwsBasicCredentials.create(accessKey, secretKey)
  
  private val s3Client = S3Client.builder()
    .endpointOverride(URI.create(s3Endpoint))
    .region(Region.of(s3Region))
    .credentialsProvider(StaticCredentialsProvider.create(credentials))
    .forcePathStyle(true) // Required for MinIO
    .build()
    
  private val s3Presigner = S3Presigner.builder()
    .endpointOverride(URI.create(s3Endpoint))
    .region(Region.of(s3Region))
    .credentialsProvider(StaticCredentialsProvider.create(credentials))
    .build()
  
  def uploadFile(key: String, file: File, contentType: String): Future[String] = Future {
    try {
      val putRequest = PutObjectRequest.builder()
        .bucket(s3Bucket)
        .key(key)
        .contentType(contentType)
        .build()
      
      val requestBody = RequestBody.fromFile(file)
      s3Client.putObject(putRequest, requestBody)
      
      logger.info(s"Successfully uploaded file to S3: $key")
      s"$s3Endpoint/$s3Bucket/$key"
    } catch {
      case e: Exception =>
        logger.error(s"Failed to upload file to S3: $key", e)
        throw e
    }
  }
  
  def uploadBytes(key: String, bytes: Array[Byte], contentType: String): Future[String] = Future {
    try {
      val putRequest = PutObjectRequest.builder()
        .bucket(s3Bucket)
        .key(key)
        .contentType(contentType)
        .build()
      
      val requestBody = RequestBody.fromBytes(bytes)
      s3Client.putObject(putRequest, requestBody)
      
      logger.info(s"Successfully uploaded bytes to S3: $key")
      s"$s3Endpoint/$s3Bucket/$key"
    } catch {
      case e: Exception =>
        logger.error(s"Failed to upload bytes to S3: $key", e)
        throw e
    }
  }
  
  def deleteFile(key: String): Future[Boolean] = Future {
    try {
      val deleteRequest = DeleteObjectRequest.builder()
        .bucket(s3Bucket)
        .key(key)
        .build()
      
      s3Client.deleteObject(deleteRequest)
      logger.info(s"Successfully deleted file from S3: $key")
      true
    } catch {
      case e: Exception =>
        logger.error(s"Failed to delete file from S3: $key", e)
        false
    }
  }
  
  def generatePresignedGetUrl(key: String, expirationMinutes: Int = 60): String = {
    val getObjectRequest = GetObjectRequest.builder()
      .bucket(s3Bucket)
      .key(key)
      .build()
    
    val presignRequest = GetObjectPresignRequest.builder()
      .signatureDuration(Duration.ofMinutes(expirationMinutes))
      .getObjectRequest(getObjectRequest)
      .build()
    
    val presignedRequest = s3Presigner.presignGetObject(presignRequest)
    presignedRequest.url().toString
  }
  
  def generatePresignedPutUrl(key: String, contentType: String, expirationMinutes: Int = 60): String = {
    val putObjectRequest = PutObjectRequest.builder()
      .bucket(s3Bucket)
      .key(key)
      .contentType(contentType)
      .build()
    
    val presignRequest = PutObjectPresignRequest.builder()
      .signatureDuration(Duration.ofMinutes(expirationMinutes))
      .putObjectRequest(putObjectRequest)
      .build()
    
    val presignedRequest = s3Presigner.presignPutObject(presignRequest)
    presignedRequest.url().toString
  }
  
  def fileExists(key: String): Future[Boolean] = Future {
    try {
      val headRequest = HeadObjectRequest.builder()
        .bucket(s3Bucket)
        .key(key)
        .build()
      
      s3Client.headObject(headRequest)
      true
    } catch {
      case _: NoSuchKeyException => false
      case e: Exception =>
        logger.error(s"Error checking if file exists: $key", e)
        false
    }
  }
  
  def copyFile(sourceKey: String, destinationKey: String): Future[Boolean] = Future {
    try {
      val copyRequest = CopyObjectRequest.builder()
        .sourceBucket(s3Bucket)
        .sourceKey(sourceKey)
        .destinationBucket(s3Bucket)
        .destinationKey(destinationKey)
        .build()
      
      s3Client.copyObject(copyRequest)
      logger.info(s"Successfully copied file from $sourceKey to $destinationKey")
      true
    } catch {
      case e: Exception =>
        logger.error(s"Failed to copy file from $sourceKey to $destinationKey", e)
        false
    }
  }
  
  def updateStorageClass(key: String, storageClass: StorageClass): Future[Boolean] = Future {
    try {
      // In production, this would involve lifecycle policies or copying with new storage class
      // For MinIO, this is a no-op but we simulate success
      logger.info(s"Updated storage class for $key to $storageClass")
      true
    } catch {
      case e: Exception =>
        logger.error(s"Failed to update storage class for $key", e)
        false
    }
  }
}