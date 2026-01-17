// File: lib/services/call_recorder_service.dart
import 'dart:async';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:phone_state/phone_state.dart';
import 'package:permission_handler/permission_handler.dart';
import 'native_recorder_bridge.dart';
import 's3_upload_service.dart';
import 'dart:developer' as developer;

class CallRecorderService {
  static final CallRecorderService _instance = CallRecorderService._internal();
  factory CallRecorderService() => _instance;
  CallRecorderService._internal();

  StreamSubscription<PhoneState>? _phoneStateSubscription;
  bool _isInitialized = false;
  String? _currentRecordingPath;

  // Prevent duplicate call events
  bool _isRecordingActive = false;
  PhoneStateStatus? _lastPhoneState;
  Timer? _debounceTimer;

  // S3 upload service
  final S3UploadService _s3Service = S3UploadService();

  /// Initialize the service and start listening for call state changes
  Future<bool> initialize() async {
    if (_isInitialized) {
      developer.log('Service already initialized', name: 'CallRecorderService');
      return true;
    }

    // Request all necessary permissions
    final permissionsGranted = await _requestPermissions();
    if (!permissionsGranted) {
      developer.log('Permissions not granted', name: 'CallRecorderService');
      return false;
    }

    // Check if Accessibility Service is enabled
    final accessibilityEnabled = await NativeRecorderBridge.isAccessibilityServiceEnabled();
    if (!accessibilityEnabled) {
      developer.log('Accessibility Service not enabled', name: 'CallRecorderService');
      return false;
    }

    // Start listening to phone state changes
    _startPhoneStateListener();
    _isInitialized = true;

    developer.log('Service initialized successfully', name: 'CallRecorderService');
    return true;
  }

  /// Configure S3 upload settings
  void configureS3({required String url, required String token}) {
    _s3Service.configure(url: url, token: token);
  }

  /// Request all required permissions
  Future<bool> _requestPermissions() async {
    final permissions = [
      Permission.microphone,
      Permission.phone,
      Permission.notification,
    ];

    // Add storage permission for Android 12 and below
    if (Platform.isAndroid) {
      final androidInfo = await _getAndroidVersion();
      if (androidInfo < 13) {
        permissions.add(Permission.storage);
      }
    }

    final statuses = await permissions.request();

    // Check if all permissions are granted
    bool allGranted = true;
    statuses.forEach((permission, status) {
      if (!status.isGranted) {
        developer.log('Permission denied: $permission', name: 'CallRecorderService');
        allGranted = false;
      }
    });

    return allGranted;
  }

  Future<int> _getAndroidVersion() async {
    return 15; // Default to Android 15
  }

  /// Start listening to phone state changes - AUTO RECORDING ENABLED
  void _startPhoneStateListener() {
    _phoneStateSubscription = PhoneState.stream.listen((phoneState) {
      developer.log('Phone State Changed: ${phoneState.status}', name: 'CallRecorderService');

      // Debounce rapid state changes
      _debounceTimer?.cancel();
      _debounceTimer = Timer(const Duration(milliseconds: 300), () {
        _handlePhoneStateChange(phoneState.status);
      });
    });
  }

  /// Handle phone state changes with debouncing - AUTO START RECORDING
  void _handlePhoneStateChange(PhoneStateStatus status) {
    // Ignore duplicate state changes
    if (_lastPhoneState == status) {
      return;
    }

    _lastPhoneState = status;

    switch (status) {
      case PhoneStateStatus.CALL_STARTED:
        if (!_isRecordingActive) {
          // AUTO START RECORDING WHEN CALL STARTS
          _onCallStarted();
        } else {
          developer.log('Call already being recorded, ignoring duplicate event', name: 'CallRecorderService');
        }
        break;
      case PhoneStateStatus.CALL_ENDED:
        if (_isRecordingActive) {
          // AUTO STOP RECORDING WHEN CALL ENDS
          _onCallEnded();
        }
        break;
      default:
        break;
    }
  }

  /// Handle call started event - AUTOMATIC RECORDING START
  Future<void> _onCallStarted() async {
    developer.log('Call Started - AUTO Recording Initiated', name: 'CallRecorderService');

    // Check if already recording
    if (_isRecordingActive) {
      developer.log('Recording already active, skipping', name: 'CallRecorderService');
      return;
    }

    try {
      // Generate file path for recording
      final filePath = await _generateRecordingFilePath();
      _currentRecordingPath = filePath;

      // Mark as active BEFORE starting to prevent race conditions
      _isRecordingActive = true;

      // Start native recording
      final success = await NativeRecorderBridge.startRecording(filePath);

      if (success) {
        developer.log('AUTO Recording started: $filePath', name: 'CallRecorderService');
      } else {
        developer.log('Failed to start AUTO recording', name: 'CallRecorderService');
        _currentRecordingPath = null;
        _isRecordingActive = false;
      }
    } catch (e) {
      developer.log('Error starting AUTO recording: $e', name: 'CallRecorderService', error: e);
      _currentRecordingPath = null;
      _isRecordingActive = false;
    }
  }

  /// Handle call ended event - AUTOMATIC RECORDING STOP + S3 UPLOAD
  Future<void> _onCallEnded() async {
    developer.log('Call Ended - Stopping Recording & Uploading', name: 'CallRecorderService');

    if (!_isRecordingActive) {
      developer.log('No active recording to stop', name: 'CallRecorderService');
      return;
    }

    try {
      final savedPath = await NativeRecorderBridge.stopRecording();

      // Mark as inactive AFTER stopping
      _isRecordingActive = false;

      if (savedPath != null) {
        developer.log('Recording saved: $savedPath', name: 'CallRecorderService');

        // Verify file exists
        final file = File(savedPath);
        if (await file.exists()) {
          final fileSize = await file.length();
          developer.log('File size: $fileSize bytes', name: 'CallRecorderService');

          // AUTO UPLOAD TO S3 if configured
          if (_s3Service.isConfigured) {
            developer.log('Starting S3 upload...', name: 'CallRecorderService');
            final uploadSuccess = await _s3Service.uploadRecording(savedPath);

            if (uploadSuccess) {
              developer.log('S3 upload successful: $savedPath', name: 'CallRecorderService');
            } else {
              developer.log('S3 upload failed: $savedPath', name: 'CallRecorderService');
            }
          } else {
            developer.log('S3 not configured, skipping upload', name: 'CallRecorderService');
          }
        }
      } else {
        developer.log('Recording not saved', name: 'CallRecorderService');
      }

      _currentRecordingPath = null;
    } catch (e) {
      developer.log('Error stopping recording: $e', name: 'CallRecorderService', error: e);
      _currentRecordingPath = null;
      _isRecordingActive = false;
    }
  }

  /// Generate unique file path for recording
  Future<String> _generateRecordingFilePath() async {
    final directory = await getApplicationDocumentsDirectory();
    final recordingsDir = Directory('${directory.path}/CallRecordings');

    // Create directory if it doesn't exist
    if (!await recordingsDir.exists()) {
      await recordingsDir.create(recursive: true);
    }

    // Generate filename with timestamp
    final timestamp = DateTime.now().millisecondsSinceEpoch;
    final fileName = 'call_recording_$timestamp.m4a';

    return '${recordingsDir.path}/$fileName';
  }

  /// Upload all pending recordings to S3
  Future<int> uploadAllPendingRecordings() async {
    return await _s3Service.uploadAllRecordings();
  }

  /// Check if service is initialized
  bool get isInitialized => _isInitialized;

  /// Get current recording path
  String? get currentRecordingPath => _currentRecordingPath;

  /// Check if S3 is configured
  bool get isS3Configured => _s3Service.isConfigured;

  /// Dispose service
  void dispose() {
    _phoneStateSubscription?.cancel();
    _debounceTimer?.cancel();
    _isInitialized = false;
    _isRecordingActive = false;
    developer.log('Service disposed', name: 'CallRecorderService');
  }

  /// Manual recording start (for testing)
  Future<bool> startManualRecording() async {
    // Prevent starting if already recording
    if (_isRecordingActive) {
      developer.log('Cannot start manual recording: already recording', name: 'CallRecorderService');
      return false;
    }

    final filePath = await _generateRecordingFilePath();
    _currentRecordingPath = filePath;
    _isRecordingActive = true;

    final success = await NativeRecorderBridge.startRecording(filePath);

    if (!success) {
      _isRecordingActive = false;
      _currentRecordingPath = null;
    }

    return success;
  }

  /// Manual recording stop (for testing)
  Future<String?> stopManualRecording() async {
    if (!_isRecordingActive) {
      developer.log('Cannot stop manual recording: not recording', name: 'CallRecorderService');
      return null;
    }

    final savedPath = await NativeRecorderBridge.stopRecording();
    _currentRecordingPath = null;
    _isRecordingActive = false;

    // Upload to S3 if configured
    if (savedPath != null && _s3Service.isConfigured) {
      await _s3Service.uploadRecording(savedPath);
    }

    return savedPath;
  }

  /// Check if currently recording
  bool get isRecording => _isRecordingActive;
}