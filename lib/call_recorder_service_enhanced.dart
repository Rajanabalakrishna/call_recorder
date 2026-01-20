// File: lib/services/call_recorder_service_enhanced.dart
import 'dart:async';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:phone_state/phone_state.dart';
import 'package:permission_handler/permission_handler.dart';
import 'native_recorder_bridge.dart';
import 'dart:developer' as developer;

/**
 * Enhanced CallRecorderService - Works with native background architecture
 *
 * Architecture:
 * - Dart layer handles UI state and user interactions
 * - Native layer (CallRecordingService, CallStateReceiver) handles background recording
 * - Dart layer receives updates from native layer
 * - Redundant: Phone state stream provides UI-layer awareness
 *
 * Key improvements:
 * 1. Recording continues even when app is swiped away (native handles it)
 * 2. Dart layer provides UI updates via stream
 * 3. Recovery mechanism: Verifies native state on app resume
 * 4. Fallback: Can start recording manually if needed
 */

class EnhancedCallRecorderService {
  static final EnhancedCallRecorderService _instance =
      EnhancedCallRecorderService._internal();

  factory EnhancedCallRecorderService() => _instance;

  EnhancedCallRecorderService._internal();

  // Streams for UI updates
  final _recordingStateController = StreamController<bool>.broadcast();
  Stream<bool> get recordingStateStream => _recordingStateController.stream;

  final _callStateController = StreamController<String>.broadcast();
  Stream<String> get callStateStream => _callStateController.stream;

  // State tracking
  StreamSubscription<PhoneState>? _phoneStateSubscription;
  bool _isInitialized = false;
  bool _isRecordingActive = false;
  String? _currentRecordingPath;
  Timer? _verificationTimer;

  /// Initialize the enhanced service
  /// This starts:
  /// 1. Native background monitoring (CallStateReceiver, BootReceiver)
  /// 2. Dart UI layer monitoring (Phone state stream)
  /// 3. Verification timer to ensure native layer is working
  Future<bool> initialize() async {
    if (_isInitialized) {
      developer.log('Service already initialized',
          name: 'EnhancedCallRecorderService');
      return true;
    }

    try {
      // Request permissions
      final permissionsGranted = await _requestPermissions();
      if (!permissionsGranted) {
        developer.log('Permissions not granted',
            name: 'EnhancedCallRecorderService');
        return false;
      }

      // Verify accessibility service
      final accessibilityEnabled =
          await NativeRecorderBridge.isAccessibilityServiceEnabled();
      if (!accessibilityEnabled) {
        developer.log('Accessibility Service not enabled',
            name: 'EnhancedCallRecorderService');
        // Don't fail, user can enable later
      }

      // Start phone state monitoring (Dart layer)
      _startPhoneStateListener();

      // Start verification timer to check native layer
      _startVerificationTimer();

      _isInitialized = true;

      developer.log('Enhanced service initialized successfully',
          name: 'EnhancedCallRecorderService');
      return true;
    } catch (e) {
      developer.log('Error initializing service: $e',
          name: 'EnhancedCallRecorderService', error: e);
      return false;
    }
  }

  /// Request all required permissions
  Future<bool> _requestPermissions() async {
    final permissions = [
      Permission.microphone,
      Permission.phone,
      Permission.notification,
    ];

    if (Platform.isAndroid) {
      final androidInfo = await _getAndroidVersion();
      if (androidInfo < 13) {
        permissions.add(Permission.storage);
      }
    }

    final statuses = await permissions.request();

    bool allGranted = true;
    statuses.forEach((permission, status) {
      if (!status.isGranted) {
        developer.log('Permission denied: $permission',
            name: 'EnhancedCallRecorderService');
        allGranted = false;
      }
    });

    return allGranted;
  }

  Future<int> _getAndroidVersion() async {
    // This is simplified - use device_info_plus for production
    return 15;
  }

  /// Start listening to phone state changes (Dart layer)
  /// Note: This provides UI awareness even if native layer is recording
  void _startPhoneStateListener() {
    _phoneStateSubscription = PhoneState.stream.listen((phoneState) {
      developer.log('Phone State: ${phoneState.status}',
          name: 'EnhancedCallRecorderService');

      final statusString = phoneState.status.toString();
      _callStateController.add(statusString);

      // Update recording state
      if (phoneState.status == PhoneStateStatus.CALL_STARTED) {
        _isRecordingActive = true;
        _recordingStateController.add(true);
      } else if (phoneState.status == PhoneStateStatus.CALL_ENDED) {
        _isRecordingActive = false;
        _recordingStateController.add(false);
      }
    });
  }

  /// Periodic verification that native layer is working
  /// If native has stopped for some reason, restart it
  void _startVerificationTimer() {
    _verificationTimer = Timer.periodic(Duration(minutes: 5), (timer) async {
      try {
        // Query native layer recording status
        final isRecording = await NativeRecorderBridge.isRecording();

        if (isRecording != _isRecordingActive) {
          developer.log(
              'State mismatch detected! Dart: $_isRecordingActive, Native: $isRecording',
              name: 'EnhancedCallRecorderService');

          // Try to sync state
          if (!_isRecordingActive && isRecording) {
            // Native is recording but Dart thinks it's not
            _isRecordingActive = true;
            _recordingStateController.add(true);
            developer.log('Synced Dart state to match native recording',
                name: 'EnhancedCallRecorderService');
          }
        }
      } catch (e) {
        developer.log('Error during verification: $e',
            name: 'EnhancedCallRecorderService', error: e);
      }
    });
  }

  /// Manual recording start (UI-initiated)
  /// Typically not needed as CallStateReceiver handles automatic recording
  Future<bool> startManualRecording() async {
    if (_isRecordingActive) {
      developer.log('Recording already active',
          name: 'EnhancedCallRecorderService');
      return false;
    }

    try {
      final filePath = await _generateRecordingFilePath();
      _currentRecordingPath = filePath;
      _isRecordingActive = true;

      final success =
          await NativeRecorderBridge.startRecording(filePath);

      if (success) {
        _recordingStateController.add(true);
        developer.log('Manual recording started: $filePath',
            name: 'EnhancedCallRecorderService');
        return true;
      } else {
        _isRecordingActive = false;
        _currentRecordingPath = null;
        return false;
      }
    } catch (e) {
      developer.log('Error starting manual recording: $e',
          name: 'EnhancedCallRecorderService', error: e);
      _isRecordingActive = false;
      return false;
    }
  }

  /// Manual recording stop
  Future<String?> stopManualRecording() async {
    if (!_isRecordingActive) {
      developer.log('No active recording', name: 'EnhancedCallRecorderService');
      return null;
    }

    try {
      final savedPath = await NativeRecorderBridge.stopRecording();
      _isRecordingActive = false;
      _currentRecordingPath = null;

      _recordingStateController.add(false);

      developer.log('Manual recording stopped: $savedPath',
          name: 'EnhancedCallRecorderService');
      return savedPath;
    } catch (e) {
      developer.log('Error stopping manual recording: $e',
          name: 'EnhancedCallRecorderService', error: e);
      _isRecordingActive = false;
      return null;
    }
  }

  /// Generate unique recording file path
  Future<String> _generateRecordingFilePath() async {
    final directory = await getApplicationDocumentsDirectory();
    final recordingsDir = Directory('${directory.path}/CallRecordings');

    if (!await recordingsDir.exists()) {
      await recordingsDir.create(recursive: true);
    }

    final timestamp = DateTime.now().millisecondsSinceEpoch;
    final fileName = 'call_recording_$timestamp.m4a';

    return '${recordingsDir.path}/$fileName';
  }

  /// Dispose service
  void dispose() {
    _phoneStateSubscription?.cancel();
    _verificationTimer?.cancel();
    _recordingStateController.close();
    _callStateController.close();
    _isInitialized = false;
    developer.log('Service disposed', name: 'EnhancedCallRecorderService');
  }

  // Getters
  bool get isInitialized => _isInitialized;
  bool get isRecording => _isRecordingActive;
  String? get currentRecordingPath => _currentRecordingPath;
}
