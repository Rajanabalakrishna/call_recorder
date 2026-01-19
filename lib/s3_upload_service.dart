// File: lib/services/s3_upload_service.dart
import 'dart:io';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'dart:developer' as developer;
import 'package:path/path.dart' as p;
import 'package:http_parser/http_parser.dart';

class S3UploadService {

  static final S3UploadService _instance = S3UploadService._internal();
  factory S3UploadService() => _instance;
  S3UploadService._internal();

  String s3Url = 'https://demand.bharatintelligence.ai/chat/api/upload_image_to_s3/';
  String authToken = 'e8fa8310c9af344ca22ec6bd23960d609b09c704';

  // Track uploaded files to avoid duplicates
  final Set<String> _uploadedFiles = <String>{};
  bool _isUploading = false;

  /// Configure S3 credentials
  void configure({required String url, required String token}) {
    s3Url = url;
    authToken = token;
    developer.log('S3 configured: $s3Url', name: 'S3UploadService');
  }

  /// Check if service is configured
  bool get isConfigured => s3Url.isNotEmpty && authToken.isNotEmpty;

  /// Upload a single file to S3
  Future<bool> uploadFile(File file) async {
    if (!isConfigured) {
      developer.log('S3 not configured', name: 'S3UploadService');
      return false;
    }

    final fileName = p.basename(file.path);

    // Skip if already uploaded
    if (_uploadedFiles.contains(fileName)) {
      developer.log('File already uploaded: $fileName', name: 'S3UploadService');
      return true;
    }

    try {
      developer.log('Uploading: $fileName', name: 'S3UploadService');

      // Create multipart request using POST as per S3 API requirement
      final request = http.MultipartRequest(
        'POST',
        Uri.parse(s3Url),
      );

      // Add headers - using 'Token' prefix as required by the API
      request.headers.addAll({
        'Authorization': 'Token $authToken',
      });

      // Add the desired S3 object name under 'name_of_image'
      request.fields['name_of_image'] = fileName;

      // Add file under 'image' field as expected by the API
      request.files.add(
        await http.MultipartFile.fromPath(
          'image',
          file.path,
          filename: fileName,
          contentType: MediaType('audio', 'm4a'),
        ),
      );

      // Send request
      final streamedResponse = await request.send();
      final response = await http.Response.fromStream(streamedResponse);

      if (response.statusCode >= 200 && response.statusCode < 300) {
        developer.log('Upload successful: $fileName', name: 'S3UploadService');
        _uploadedFiles.add(fileName);
        return true;
      } else {
        developer.log(
          'Upload failed: ${response.statusCode} - ${response.body}',
          name: 'S3UploadService',
        );
        return false;
      }
    } catch (e) {
      developer.log('Upload error: $e', name: 'S3UploadService', error: e);
      return false;
    }
  }

  /// Scan recordings folder and upload all files
  Future<int> uploadAllRecordings() async {
    if (!isConfigured) {
      developer.log('S3 not configured', name: 'S3UploadService');
      return 0;
    }

    if (_isUploading) {
      developer.log('Upload already in progress', name: 'S3UploadService');
      return 0;
    }

    _isUploading = true;
    int uploadedCount = 0;

    try {
      final directory = await getApplicationDocumentsDirectory();
      final recordingsDir = Directory('${directory.path}/CallRecordings');

      if (!await recordingsDir.exists()) {
        developer.log('Recordings directory not found', name: 'S3UploadService');
        _isUploading = false;
        return 0;
      }

      // Get all .m4a files
      final files = recordingsDir
          .listSync()
          .whereType<File>()
          .where((file) => file.path.endsWith('.m4a'))
          .toList();

      developer.log('Found ${files.length} recordings to upload', name: 'S3UploadService');

      // Upload each file
      for (final file in files) {
        final success = await uploadFile(file);
        if (success) {
          uploadedCount++;
        }

        // Small delay between uploads to avoid rate limiting
        await Future.delayed(const Duration(milliseconds: 500));
      }

      developer.log('Uploaded $uploadedCount files', name: 'S3UploadService');
    } catch (e) {
      developer.log('Error scanning recordings: $e', name: 'S3UploadService', error: e);
    } finally {
      _isUploading = false;
    }

    return uploadedCount;
  }

  /// Upload a specific recording after it's saved
  Future<bool> uploadRecording(String filePath) async {
    if (!isConfigured) {
      return false;
    }

    final file = File(filePath);
    if (!await file.exists()) {
      developer.log('File not found: $filePath', name: 'S3UploadService');
      return false;
    }

    return await uploadFile(file);
  }

  /// Get list of uploaded file names
  Set<String> get uploadedFiles => Set.from(_uploadedFiles);

  /// Clear uploaded files cache
  void clearCache() {
    _uploadedFiles.clear();
  }
}
