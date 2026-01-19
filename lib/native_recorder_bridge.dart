// File: lib/services/native_recorder_bridge.dart
import 'package:flutter/services.dart';
import 'dart:developer' as developer;

/// Bridge to communicate with native Kotlin Accessibility Service
class NativeRecorderBridge {

  static const MethodChannel _channel = MethodChannel('com.example.recorder/native');

  /// Start recording to specified file path
  /// Returns true if recording started successfully
  static Future<bool> startRecording(String filePath) async {
    try {
      final result = await _channel.invokeMethod<bool>('startRecording', {
        'filePath': filePath,
      });
      developer.log('Native recording started: $result', name: 'RecorderBridge');
      return result ?? false;
    } on PlatformException catch (e) {
      developer.log('Error starting recording: ${e.message}', name: 'RecorderBridge', error: e);
      return false;
    }
  }

  /// Stop current recording
  /// Returns the file path where recording was saved, or null if failed
  static Future<String?> stopRecording() async {
    try {
      final result = await _channel.invokeMethod<String>('stopRecording');
      developer.log('Native recording stopped: $result', name: 'RecorderBridge');
      return result;
    } on PlatformException catch (e) {
      developer.log('Error stopping recording: ${e.message}', name: 'RecorderBridge', error: e);
      return null;
    }
  }

  /// Check if currently recording
  static Future<bool> isRecording() async {
    try {
      final result = await _channel.invokeMethod<bool>('isRecording');
      return result ?? false;
    } on PlatformException catch (e) {
      developer.log('Error checking recording status: ${e.message}', name: 'RecorderBridge', error: e);
      return false;
    }
  }

  /// Open device Accessibility Settings
  static Future<void> openAccessibilitySettings() async {
    try {

      await _channel.invokeMethod('openAccessibilitySettings');

    } on PlatformException catch (e) {
      developer.log('Error opening settings: ${e.message}', name: 'RecorderBridge', error: e);
    }
  }

  /// Check if Accessibility Service is enabled
  static Future<bool> isAccessibilityServiceEnabled() async {
    try {
      final result = await _channel.invokeMethod<bool>('isAccessibilityServiceEnabled');
      return result ?? false;
    } catch (e) {
      return false;
    }
  }

  /// Start foreground service (keeps app alive during recording)
  static Future<void> startForegroundService() async {
    try {
      await _channel.invokeMethod('startForegroundService');
      developer.log('Foreground service started', name: 'RecorderBridge');
    } on PlatformException catch (e) {
      developer.log('Error starting foreground service: ${e.message}', name: 'RecorderBridge', error: e);
    }
  }

  /// Stop foreground service
  static Future<void> stopForegroundService() async {
    try {
      await _channel.invokeMethod('stopForegroundService');
      developer.log('Foreground service stopped', name: 'RecorderBridge');
    } on PlatformException catch (e) {
      developer.log('Error stopping foreground service: ${e.message}', name: 'RecorderBridge', error: e);
    }
  }

  /// Get the native recordings directory path
  /// This ensures Flutter and native code use the same directory
  static Future<String?> getRecordingsDirectory() async {
    try {
      final result = await _channel.invokeMethod<String>('getRecordingsDirectory');
      developer.log('Recordings directory: $result', name: 'RecorderBridge');
      return result;
    } on PlatformException catch (e) {
      developer.log('Error getting recordings directory: ${e.message}', name: 'RecorderBridge', error: e);
      return null;
    }
  }

  /// Configure S3 settings (saved in native SharedPreferences)
  static Future<bool> configureS3(String s3Url, String authToken) async {
    try {
      final result = await _channel.invokeMethod<bool>('configureS3', {
        's3Url': s3Url,
        'authToken': authToken,
      });
      developer.log('S3 configured: $result', name: 'RecorderBridge');
      return result ?? false;
    } on PlatformException catch (e) {
      developer.log('Error configuring S3: ${e.message}', name: 'RecorderBridge', error: e);
      return false;
    }
  }

  /// Get S3 configuration from native SharedPreferences
  static Future<Map<String, dynamic>> getS3Config() async {
    try {
      final result = await _channel.invokeMethod<Map>('getS3Config');
      return Map<String, dynamic>.from(result ?? {});
    } on PlatformException catch (e) {
      developer.log('Error getting S3 config: ${e.message}', name: 'RecorderBridge', error: e);
      return {};
    }
  }

  /// Upload all recordings to S3 (runs in native background thread)
  static Future<int> uploadAllToS3() async {
    try {
      final result = await _channel.invokeMethod<int>('uploadAllToS3');
      developer.log('Uploaded $result recordings', name: 'RecorderBridge');
      return result ?? 0;
    } on PlatformException catch (e) {
      developer.log('Error uploading to S3: ${e.message}', name: 'RecorderBridge', error: e);
      return 0;
    }
  }

  static Future<void> requestBatteryOptimization() async {
    await _channel.invokeMethod('requestBatteryOptimization');
  }

  static Future<bool> isBatteryOptimizationDisabled() async {
    final result = await _channel.invokeMethod<bool>('isBatteryOptimizationDisabled');
    return result ?? false;
  }

}